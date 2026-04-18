package custom.components;

import com.google.common.util.concurrent.RateLimiter;
import custom.common.CommonFunction;
import custom.entity.DeleteParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DeleteResult;
import custom.entity.result.ResultEnum;
import custom.entity.PKFieldInfo;
import custom.utils.MathUtil;
import custom.utils.PeriodicStatsReporter;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Data
@Slf4j
public class DeleteComp {
    public static DeleteResult delete(DeleteParams deleteParams) {
        String collection = (deleteParams.getCollectionName() == null ||
                deleteParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : deleteParams.getCollectionName();

        // 如果 numConcurrency <= 1 且 runningMinutes <= 0，走单次删除（兼容旧行为）
        if (deleteParams.getNumConcurrency() <= 1 && deleteParams.getRunningMinutes() <= 0) {
            return deleteSingle(deleteParams, collection);
        }

        // 并发持续删除模式：每轮先 query 出 PK，再按 PK 删除
        return deleteConcurrent(deleteParams, collection);
    }

    /**
     * 单次删除（兼容旧行为）
     */
    private static DeleteResult deleteSingle(DeleteParams deleteParams, String collection) {
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
            DeleteResp deleteResp = milvusClientV2.delete(deleteReq);
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
     * 并发持续删除模式：每轮 query PK -> delete by PK
     */
    private static DeleteResult deleteConcurrent(DeleteParams deleteParams, String collection) {
        int numConcurrency = Math.max(deleteParams.getNumConcurrency(), 1);
        long runningMinutes = Math.max(deleteParams.getRunningMinutes(), 1);
        int deleteNumPerRound = deleteParams.getDeleteNumPerRound() > 0 ? deleteParams.getDeleteNumPerRound() : 10;

        // 获取 PK 字段信息
        PKFieldInfo pkFieldInfo = CommonFunction.getPKFieldInfo(collection);
        log.info("delete collection: {}, pkField: {}, pkType: {}", collection, pkFieldInfo.getFieldName(), pkFieldInfo.getDataType());

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
                long lastPrintTime = System.currentTimeMillis();
                while (LocalDateTime.now().isBefore(endTime)) {
                    if (finalRateLimiter != null) {
                        finalRateLimiter.acquire();
                    }
                    long startItemTime = System.currentTimeMillis();
                    try {
                        // Step 1: query 出一批 PK
                        String filterStr;
                        if (pkFieldInfo.getDataType() == io.milvus.v2.common.DataType.VarChar) {
                            filterStr = pkFieldInfo.getFieldName() + " > \"0\" ";
                        } else {
                            filterStr = pkFieldInfo.getFieldName() + " >= 0 ";
                        }
                        QueryResp queryResp = milvusClientV2.query(QueryReq.builder()
                                .collectionName(collection)
                                .filter(filterStr)
                                .outputFields(java.util.Collections.singletonList(pkFieldInfo.getFieldName()))
                                .limit(deleteNumPerRound)
                                .consistencyLevel(ConsistencyLevel.STRONG)
                                .build());

                        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                        if (queryResults == null || queryResults.isEmpty()) {
                            log.info("线程[{}] query 无数据可删，退出循环", finalI);
                            break;
                        }

                        // Step 2: 提取 PK 列表
                        List<Object> pkIds = new ArrayList<>();
                        for (QueryResp.QueryResult qr : queryResults) {
                            pkIds.add(qr.getEntity().get(pkFieldInfo.getFieldName()));
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
                        DeleteResp deleteResp = milvusClientV2.delete(deleteReq);
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

    @Data
    public static class DeleteItemResult {
        private List<Float> costTime;
        private List<Long> deleteCountList;
    }
}
