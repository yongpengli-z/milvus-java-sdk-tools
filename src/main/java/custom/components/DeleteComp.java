package custom.components;

import com.google.common.util.concurrent.RateLimiter;
import custom.common.CommonFunction;
import custom.entity.DeleteParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DeleteResult;
import custom.entity.result.ResultEnum;
import custom.exception.CustomException;
import custom.exception.CustomExceptionCode;
import custom.utils.MathUtil;
import custom.utils.PeriodicStatsReporter;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static custom.BaseTest.*;

@Data
@Slf4j
public class DeleteComp {
    public static DeleteResult delete(DeleteParams deleteParams) {
        boolean multiMode = (deleteParams.getCollectionNamePrefix() != null
                && !deleteParams.getCollectionNamePrefix().equalsIgnoreCase(""))
                || deleteParams.getCollectionRangeStart() >= 0;
        if (multiMode) {
            return deleteMulti(deleteParams);
        }

        MilvusClientV2 client = getMilvusClient(deleteParams.getTargetEndpoint());
        log.info("Delete 使用 endpoint: {}", describeTargetEndpoint(deleteParams.getTargetEndpoint()));

        String collection = (deleteParams.getCollectionName() == null ||
                deleteParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : deleteParams.getCollectionName();

        // 如果 numConcurrency <= 1 且 runningMinutes <= 0，走单次删除（兼容旧行为）
        if (deleteParams.getNumConcurrency() <= 1 && deleteParams.getRunningMinutes() <= 0) {
            return deleteSingle(client, deleteParams, collection);
        }

        // 并发持续删除模式：每轮先 query 出 PK，再按 PK 删除
        return deleteConcurrent(client, deleteParams, collection);
    }

    /**
     * 多 collection 模式：对池子中命中的每个 collection 各执行一次单发删除（按 ids/filter）；
     * numConcurrency 语义为「并发 collection 数」。
     */
    private static DeleteResult deleteMulti(DeleteParams deleteParams) {
        List<String> targetCollections = CommonFunction.filterCollectionPool(globalCollectionNames,
                deleteParams.getCollectionNamePrefix(), deleteParams.getCollectionRangeStart(), deleteParams.getCollectionRangeEnd());
        MilvusClientV2 client = getMilvusClient(deleteParams.getTargetEndpoint());
        int numConcurrency = Math.max(deleteParams.getNumConcurrency(), 1);
        int workers = Math.min(numConcurrency, targetCollections.size());
        log.info("Delete 多 collection 模式：共 {} 个 collection，并发 collection 数 {}，每个单发删除",
                targetCollections.size(), workers);

        long startTimeTotal = System.currentTimeMillis();
        DeleteResult[] slotResults = new DeleteResult[targetCollections.size()];
        AtomicInteger cursor = new AtomicInteger(0);
        AtomicInteger threadIndex = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(workers,
                runnable -> new Thread(runnable, "delete-multi-worker-" + threadIndex.getAndIncrement()));
        try {
            for (int i = 0; i < workers; i++) {
                executorService.submit(() -> {
                    int idx;
                    while ((idx = cursor.getAndIncrement()) < targetCollections.size()) {
                        slotResults[idx] = deleteSingle(client, deleteParams, targetCollections.get(idx));
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(6, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Delete 多 collection 并发执行被中断: {}", e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
        float totalCostTime = (float) ((System.currentTimeMillis() - startTimeTotal) / 1000.00);

        long success = 0, fail = 0, totalDeleted = 0;
        List<String> assertMessages = new ArrayList<>();
        for (int i = 0; i < targetCollections.size(); i++) {
            DeleteResult r = slotResults[i];
            String cn = targetCollections.get(i);
            if (r == null || r.getCommonResult() == null
                    || !ResultEnum.SUCCESS.result.equals(r.getCommonResult().getResult())) {
                fail++;
                String msg = (r != null && r.getCommonResult() != null) ? r.getCommonResult().getMessage() : "delete not executed (interrupted)";
                assertMessages.add("[ASSERT FAIL] delete [" + cn + "] failed: " + msg);
                continue;
            }
            success++;
            totalDeleted += (r.getDeletedCount() == null ? 0L : r.getDeletedCount());
        }
        if (!assertMessages.isEmpty()) {
            log.warn("Delete(multi) assertions: " + assertMessages);
        }
        double passRate = targetCollections.isEmpty() ? 0 : 100.0 * success / targetCollections.size();
        CommonResult commonResult = CommonResult.builder()
                .result(fail == 0 ? ResultEnum.SUCCESS.result
                        : (success == 0 ? ResultEnum.FAIL.result : ResultEnum.WARNING.result))
                .message(fail == 0 ? "" : fail + "/" + targetCollections.size() + " collections delete failed")
                .build();
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
        return DeleteResult.builder()
                .deletedCount(totalDeleted)
                .concurrencyNum(workers)
                .costTime(totalCostTime)
                .rps(totalCostTime > 0 ? (float) (success / (double) totalCostTime) : 0f)
                .requestNum(success)
                .passRate(passRate)
                .commonResult(commonResult)
                .assertMessages(assertMessages)
                .totalCount(targetCollections.size())
                .successCount(success)
                .failCount(fail)
                .truncated(false)
                .build();
    }

    /**
     * 单次删除（兼容旧行为）
     */
    private static DeleteResult deleteSingle(MilvusClientV2 client, DeleteParams deleteParams, String collection) {
        CommonResult commonResult = CommonResult.builder().build();
        List<String> assertMessages = new ArrayList<>();

        try {
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(collection)
                    .partitionName(deleteParams.getPartitionName())
                    .build();
            if (deleteParams.getIds() != null && deleteParams.getIds().size() > 0) {
                deleteReq.setIds(deleteParams.getIds());
                log.info("待删除ID列表({}条): {}", deleteParams.getIds().size(), deleteParams.getIds());
            }
            if (deleteParams.getFilter() != null && !deleteParams.getFilter().equalsIgnoreCase("")) {
                deleteReq.setFilter(deleteParams.getFilter());
            }
            DeleteResp deleteResp = client.delete(deleteReq);
            long deleteCnt = deleteResp.getDeleteCnt();
            commonResult.setResult(ResultEnum.SUCCESS.result);
            if (deleteCnt == 0) {
                assertMessages.add("[ASSERT WARN] delete deletedCount == 0, no entities were deleted");
            }
            if (!assertMessages.isEmpty()) {
                log.warn("Delete assertions: " + assertMessages);
            }
            return DeleteResult.builder().deletedCount(deleteCnt).commonResult(commonResult).assertMessages(assertMessages).build();
        } catch (Exception e) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
            commonResult.setMessage(e.getMessage());
            assertMessages.add("[ASSERT FAIL] delete exception: " + e.getMessage());
            log.warn("Delete assertions: " + assertMessages);
            return DeleteResult.builder().commonResult(commonResult).assertMessages(assertMessages).build();
        }
    }

    /**
     * 并发持续删除模式：每轮用随机向量 search 出分散的 PK，再按 PK 删除
     */
    private static DeleteResult deleteConcurrent(MilvusClientV2 client, DeleteParams deleteParams, String collection) {
        int numConcurrency = Math.max(deleteParams.getNumConcurrency(), 1);
        long runningMinutes = Math.max(deleteParams.getRunningMinutes(), 1);
        int deleteNumPerRound = deleteParams.getDeleteNumPerRound() > 0 ? deleteParams.getDeleteNumPerRound() : 10;

        // 获取第一个向量字段名
        String annsField = getFirstVectorField(client, collection);
        log.info("delete collection: {}, annsField: {}", collection, annsField);

        // 从 collection 中捞取随机向量用于 search
        log.info("从collection里捞取向量用于search-then-delete...");
        List<BaseVector> searchBaseVectors = CommonFunction.providerSearchVectorDataset(client, collection, 1000, annsField);
        log.info("提供给search使用的随机向量数: {}", searchBaseVectors.size());
        if (searchBaseVectors.isEmpty()) {
            log.error("无法从collection中获取向量数据，无法执行持续删除");
            CommonResult commonResult = CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message("无法从collection中获取向量数据")
                    .build();
            return DeleteResult.builder().commonResult(commonResult).build();
        }

        ArrayList<Future<DeleteItemResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(numConcurrency);
        float deleteTotalTime;
        long startTimeTotal = System.currentTimeMillis();
        log.info("delete参数：{}", deleteParams);

        // 创建 RateLimiter
        RateLimiter rateLimiter = null;
        if (deleteParams.getTargetQps() > 0) {
            rateLimiter = RateLimiter.create(deleteParams.getTargetQps());
            log.info("启用QPS控制: {} 请求/秒", deleteParams.getTargetQps());
        }

        PeriodicStatsReporter statsReporter = new PeriodicStatsReporter("Delete");
        statsReporter.start();
        for (int i = 0; i < numConcurrency; i++) {
            int finalI = i;
            RateLimiter finalRateLimiter = rateLimiter;
            Callable<DeleteItemResult> callable = () -> {
                log.info("线程[{}]启动...", finalI);
                DeleteItemResult deleteItemResult = new DeleteItemResult();
                List<Long> deleteCountList = new ArrayList<>();
                List<Float> costTime = new ArrayList<>();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(runningMinutes);
                Random random = new Random();
                long lastPrintTime = System.currentTimeMillis();
                while (LocalDateTime.now().isBefore(endTime)) {
                    if (finalRateLimiter != null) {
                        finalRateLimiter.acquire();
                    }
                    long startItemTime = System.currentTimeMillis();
                    try {
                        // Step 1: 随机选一个向量做 search，找出分散的 PK
                        BaseVector randomVector = searchBaseVectors.get(random.nextInt(searchBaseVectors.size()));
                        SearchReq.SearchReqBuilder searchReqBuilder = SearchReq.builder()
                                .collectionName(collection)
                                .annsField(annsField)
                                .data(Collections.singletonList(randomVector))
                                .topK(deleteNumPerRound)
                                .consistencyLevel(ConsistencyLevel.STRONG);
                        // 将 filter 作为 search 的过滤条件，只删除符合条件的数据
                        if (deleteParams.getFilter() != null && !deleteParams.getFilter().isEmpty()) {
                            searchReqBuilder.filter(deleteParams.getFilter());
                        }
                        SearchResp searchResp = client.search(searchReqBuilder.build());

                        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
                        if (searchResults == null || searchResults.isEmpty() || searchResults.get(0).isEmpty()) {
                            log.info("线程[{}] search 无数据可删，退出循环", finalI);
                            break;
                        }

                        // Step 2: 提取 PK 列表
                        List<Object> pkIds = new ArrayList<>();
                        for (SearchResp.SearchResult sr : searchResults.get(0)) {
                            pkIds.add(sr.getId());
                        }

                        if (pkIds.size() < deleteNumPerRound) {
                            log.warn("线程[{}] search 返回{}条，不足预期{}条，collection数据可能即将删完",
                                    finalI, pkIds.size(), deleteNumPerRound);
                        }
                        log.info("线程[{}] 本轮待删除PK列表({}条): {}", finalI, pkIds.size(), pkIds);

                        // Step 3: 按 PK 删除
                        DeleteReq deleteReq = DeleteReq.builder()
                                .collectionName(collection)
                                .build();
                        deleteReq.setIds(pkIds);
                        if (deleteParams.getPartitionName() != null && !deleteParams.getPartitionName().isEmpty()) {
                            deleteReq.setPartitionName(deleteParams.getPartitionName());
                        }
                        DeleteResp deleteResp = client.delete(deleteReq);
                        deleteCountList.add(deleteResp.getDeleteCnt());
                    } catch (Exception e) {
                        statsReporter.recordFailure();
                        log.error("delete exception: {}", e.getMessage());
                        deleteCountList.add(-1L);
                    }
                    long endItemTime = System.currentTimeMillis();
                    float costTimeItem = (float) ((endItemTime - startItemTime) / 1000.00);
                    costTime.add(costTimeItem);
                    statsReporter.recordCostTime(costTimeItem);
                    if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                        log.info("线程[{}] 已经 delete: {}次", finalI, deleteCountList.size());
                        lastPrintTime = System.currentTimeMillis();
                    }
                }
                deleteItemResult.setDeleteCountList(deleteCountList);
                deleteItemResult.setCostTime(costTime);
                return deleteItemResult;
            };
            Future<DeleteItemResult> future = executorService.submit(callable);
            list.add(future);
        }

        long requestNum = 0;
        long successNum = 0;
        long totalDeletedCount = 0;
        CommonResult commonResult;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<DeleteItemResult> future : list) {
            try {
                DeleteItemResult deleteItemResult = future.get();
                requestNum += deleteItemResult.getDeleteCountList().size();
                successNum += deleteItemResult.getDeleteCountList().stream().filter(x -> x >= 0).count();
                totalDeletedCount += deleteItemResult.getDeleteCountList().stream().filter(x -> x > 0).mapToLong(Long::longValue).sum();
                costTimeTotal.addAll(deleteItemResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("delete 统计异常: {}", e.getMessage());
                commonResult = CommonResult.builder()
                        .message("delete 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                return DeleteResult.builder().commonResult(commonResult).build();
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        deleteTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        double passRate = requestNum > 0 ? 100.0 * successNum / requestNum : 0;
        log.info("Total delete {}次 ,cost: {} seconds! pass rate: {}%", requestNum, deleteTotalTime, (float) passRate);
        log.info("Total 线程数 {} ,RPS avg: {}", numConcurrency, requestNum / deleteTotalTime);
        log.info("Total deleted count: {}", totalDeletedCount);
        log.info("Avg:" + MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:" + MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:" + MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:" + MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:" + MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:" + MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:" + MathUtil.calculateTP99(costTimeTotal, 0.50f));
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        List<String> assertMessages = new ArrayList<>();
        if (requestNum == 0) {
            assertMessages.add("[ASSERT FAIL] delete requestNum == 0, no delete was executed");
        }
        if (passRate < 50.0f) {
            assertMessages.add(String.format("[ASSERT FAIL] delete passRate=%.2f%% < 50%%, %d/%d requests succeeded",
                    passRate, successNum, requestNum));
        } else if (passRate < 100.0f) {
            assertMessages.add(String.format("[ASSERT WARN] delete passRate=%.2f%% < 100%%, %d/%d requests succeeded",
                    passRate, successNum, requestNum));
        }
        if (!assertMessages.isEmpty()) {
            log.warn("Delete assertions: " + assertMessages);
        }
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
        DeleteResult deleteResult = DeleteResult.builder()
                .deletedCount(totalDeletedCount)
                .rps(requestNum / deleteTotalTime)
                .concurrencyNum(numConcurrency)
                .costTime(deleteTotalTime)
                .requestNum(requestNum)
                .passRate(passRate)
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .commonResult(commonResult)
                .assertMessages(assertMessages)
                .build();
        statsReporter.stop();
        executorService.shutdown();
        return deleteResult;
    }

    /**
     * 获取 collection 的第一个向量字段名
     */
    private static String getFirstVectorField(MilvusClientV2 client, String collectionName) {
        DescribeCollectionResp resp = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(collectionName).build());
        for (CreateCollectionReq.FieldSchema field : resp.getCollectionSchema().getFieldSchemaList()) {
            DataType dt = field.getDataType();
            if (dt == DataType.FloatVector || dt == DataType.BinaryVector ||
                    dt == DataType.Float16Vector || dt == DataType.BFloat16Vector ||
                    dt == DataType.SparseFloatVector || dt == DataType.Int8Vector) {
                return field.getName();
            }
        }
        throw new CustomException(CustomExceptionCode.VECTOR_FIELD_NOT_FOUND,
                "collection [" + collectionName + "] 没有向量字段");
    }

    @Data
    public static class DeleteItemResult {
        private List<Float> costTime;
        private List<Long> deleteCountList;
    }
}
