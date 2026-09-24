package custom.components;

import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import custom.entity.UpsertParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UpsertResult;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.pojo.UpdateFieldName;
import custom.utils.PeriodicStatsReporter;
import custom.utils.RetryLogUtil;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static custom.BaseTest.*;

@Slf4j
public class UpsertComp {

    /**
     * 入口：判断单 collection / 多 collection 模式。
     * 设置 collectionNamePrefix（非空）或 collectionRangeStart（>=0）时进入多 collection 模式：
     * 对 globalCollectionNames 池子按前缀+区间过滤后，**每个** collection 各 upsert numEntries 条。
     */
    public static UpsertResult upsertCollection(UpsertParams upsertParams) {
        boolean multiMode = (upsertParams.getCollectionNamePrefix() != null
                && !upsertParams.getCollectionNamePrefix().equalsIgnoreCase(""))
                || upsertParams.getCollectionRangeStart() >= 0;
        // 数据集信息只依赖 fieldDataSourceList，与 collection 无关：整个 Upsert 步骤只预加载一次，
        // 多 collection 模式下所有 collection 共用同一套数据集，避免逐 collection 重复遍历检查数据集文件
        Map<String, InsertComp.FieldDatasetInfo> fieldDatasetInfoMap =
                InsertComp.preloadFieldDatasetInfo(upsertParams.getFieldDataSourceList());
        if (multiMode) {
            return upsertMulti(upsertParams, fieldDatasetInfoMap);
        }
        return doUpsertOne(upsertParams, resolveSingleCollectionName(upsertParams), fieldDatasetInfoMap);
    }

    /** 单 collection 模式：按 collectionRule 从池子/显式名解析目标 collection。 */
    private static String resolveSingleCollectionName(UpsertParams upsertParams) {
        String collectionName;
        Random random = new Random();
        if (upsertParams.getCollectionRule() == null || upsertParams.getCollectionRule().equalsIgnoreCase("")) {
            collectionName = (upsertParams.getCollectionName() == null ||
                    upsertParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : upsertParams.getCollectionName();
        } else if (upsertParams.getCollectionRule().equalsIgnoreCase("random")) {
            collectionName = globalCollectionNames.get(random.nextInt(globalCollectionNames.size()));
        } else if (upsertParams.getCollectionRule().equalsIgnoreCase("sequence")) {
            collectionName = globalCollectionNames.get(upsertCollectionIndex);
            upsertCollectionIndex += 1;
            upsertCollectionIndex = upsertCollectionIndex % globalCollectionNames.size();
        } else {
            collectionName = (upsertParams.getCollectionName() == null ||
                    upsertParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : upsertParams.getCollectionName();
        }
        return collectionName;
    }

    /** 多 collection 模式：并发（numConcurrency=并发 collection 数）对每个命中 collection upsert numEntries 条。 */
    private static UpsertResult upsertMulti(UpsertParams upsertParams,
                                            Map<String, InsertComp.FieldDatasetInfo> fieldDatasetInfoMap) {
        List<String> targetCollections = CommonFunction.filterCollectionPool(globalCollectionNames,
                upsertParams.getCollectionNamePrefix(), upsertParams.getCollectionRangeStart(), upsertParams.getCollectionRangeEnd());
        int numConcurrency = Math.max(upsertParams.getNumConcurrency(), 1);
        log.info("Upsert 多 collection 模式：共 {} 个 collection，并发 collection 数 {}，每个 upsert {} 条",
                targetCollections.size(), Math.min(numConcurrency, targetCollections.size()), upsertParams.getNumEntries());

        long startTimeTotal = System.currentTimeMillis();
        UpsertResult[] slotResults = new UpsertResult[targetCollections.size()];
        int workers = Math.min(numConcurrency, targetCollections.size());
        AtomicInteger cursor = new AtomicInteger(0);
        AtomicInteger threadIndex = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(workers,
                runnable -> new Thread(runnable, "upsert-multi-worker-" + threadIndex.getAndIncrement()));
        try {
            for (int i = 0; i < workers; i++) {
                executorService.submit(() -> {
                    int count = 0;
                    int idx;
                    while ((idx = cursor.getAndIncrement()) < targetCollections.size()) {
                        slotResults[idx] = doUpsertOne(upsertParams, targetCollections.get(idx), fieldDatasetInfoMap);
                        count++;
                    }
                    log.info("线程[{}] 完成，共 upsert {} 个 collection", Thread.currentThread().getName(), count);
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(6, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Upsert 多 collection 并发执行被中断: {}", e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
        float totalCostTime = (float) ((System.currentTimeMillis() - startTimeTotal) / 1000.00);

        long success = 0, fail = 0, totalEntries = 0;
        List<String> assertMessages = new ArrayList<>();
        for (int i = 0; i < targetCollections.size(); i++) {
            UpsertResult r = slotResults[i];
            String cn = targetCollections.get(i);
            if (r == null || r.getCommonResult() == null
                    || !ResultEnum.SUCCESS.result.equals(r.getCommonResult().getResult())) {
                fail++;
                String msg = (r != null && r.getCommonResult() != null) ? r.getCommonResult().getMessage() : "upsert not executed (interrupted)";
                assertMessages.add("[ASSERT FAIL] upsert [" + cn + "] failed: " + msg);
                continue;
            }
            success++;
            totalEntries += r.getNumEntries();
        }
        if (!assertMessages.isEmpty()) {
            log.warn("Upsert(multi) assertions: " + assertMessages);
        }
        CommonResult commonResult = CommonResult.builder()
                .result(fail == 0 ? ResultEnum.SUCCESS.result
                        : (success == 0 ? ResultEnum.FAIL.result : ResultEnum.WARNING.result))
                .message(fail == 0 ? "" : fail + "/" + targetCollections.size() + " collections upsert failed")
                .build();
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
        return UpsertResult.builder()
                .commonResult(commonResult)
                .numEntries(totalEntries)
                .requestNum(success)
                .costTime(totalCostTime)
                .rps(totalCostTime > 0 ? totalEntries / (double) totalCostTime : 0d)
                .assertMessages(assertMessages)
                .totalCount(targetCollections.size())
                .successCount(success)
                .failCount(fail)
                .truncated(false)
                .build();
    }

    /** 单 collection upsert：把 numEntries 按 batchSize 分批 upsert 到指定 collection。数据集信息由入口统一预加载后传入。 */
    private static UpsertResult doUpsertOne(UpsertParams upsertParams, String collectionName,
                                            Map<String, InsertComp.FieldDatasetInfo> fieldDatasetInfoMap) {
        MilvusClientV2 client = getMilvusClient(upsertParams.getTargetEndpoint());
        log.info("Upsert 使用 endpoint: {}", describeTargetEndpoint(upsertParams.getTargetEndpoint()));

        //先处理upsert里数据生成的规则，先进行排序处理
        if (upsertParams.getGeneralDataRoleList() != null && upsertParams.getGeneralDataRoleList().size() > 0) {
            for (GeneralDataRole generalDataRole : upsertParams.getGeneralDataRoleList()) {
                List<RandomRangeParams> randomRangeParamsList = generalDataRole.getRandomRangeParamsList();
                randomRangeParamsList.sort(Comparator.comparing(RandomRangeParams::getStart));
            }
        }
        // 要循环upsert的次数--insertRounds
        long upsertRounds = upsertParams.getNumEntries() / upsertParams.getBatchSize();
        float upsertTotalTime = 0;
        log.info("Upsert collection [" + collectionName + "]  from id:" + upsertParams.getStartId() + " , total " + upsertParams.getNumEntries() + " entities... ");
        if (upsertParams.isPartialUpdate()) {
            List<String> fieldNames = upsertParams.getUpdateFieldNames() == null ? Collections.emptyList()
                    : upsertParams.getUpdateFieldNames().stream()
                    .map(UpdateFieldName::getFieldName)
                    .filter(fn -> fn != null && !fn.isEmpty())
                    .collect(Collectors.toList());
            log.info("Partial Update enabled, update fields: " + fieldNames);
        }
        long startTimeTotal = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(upsertParams.getNumConcurrency());
        ArrayList<Future<UpsertComp.UpsertResultItem>> list = new ArrayList<>();
        // 提前获取collectionSchema，避免每次生成数据时候重复调用describe接口
        DescribeCollectionResp describeCollectionResp = client.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());

        // pkFromFilter：先按 filter 查询现有 PK，作为 upsert 行的主键来源（验证 autoID upsert PK 保留等场景）
        final String primaryFieldName = describeCollectionResp.getPrimaryFieldName();
        List<Object> pkPool = null;
        if (upsertParams.getPkFromFilter() != null && !upsertParams.getPkFromFilter().isEmpty()) {
            long pkLimit = Math.max(1, Math.min(upsertParams.getNumEntries(), 16384));
            QueryResp pkResp = client.query(QueryReq.builder()
                    .collectionName(collectionName)
                    .outputFields(Collections.singletonList(primaryFieldName))
                    .filter(upsertParams.getPkFromFilter())
                    .consistencyLevel(ConsistencyLevel.STRONG)
                    .limit(pkLimit)
                    .build());
            pkPool = new ArrayList<>();
            if (pkResp.getQueryResults() != null) {
                for (QueryResp.QueryResult qr : pkResp.getQueryResults()) {
                    Object pk = qr.getEntity() == null ? null : qr.getEntity().get(primaryFieldName);
                    if (pk != null) {
                        pkPool.add(pk);
                    }
                }
            }
            log.info("pkFromFilter [{}] 命中 {} 个现有 PK，将作为 upsert 主键来源", upsertParams.getPkFromFilter(), pkPool.size());
            if (pkPool.isEmpty()) {
                return UpsertResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.FAIL.result)
                                .message("pkFromFilter 未查到任何 PK: " + upsertParams.getPkFromFilter()).build())
                        .assertMessages(Collections.singletonList("[ASSERT FAIL] pkFromFilter 查询无结果: " + upsertParams.getPkFromFilter()))
                        .build();
            }
        }
        final List<Object> finalPkPool = pkPool;

        // 1. 创建RateLimiter实例（根据配置的QPS）
        RateLimiter rateLimiter = null;
        if (upsertParams.getTargetQps() > 0) {
            rateLimiter = RateLimiter.create(upsertParams.getTargetQps());
            log.info("启用QPS控制: {} 请求/秒", upsertParams.getTargetQps());
        }

        // upsert data with multiple threads
        PeriodicStatsReporter statsReporter = new PeriodicStatsReporter("Upsert");
        statsReporter.start();
        Map<String, InsertComp.FieldDatasetInfo> finalFieldDatasetInfoMap = fieldDatasetInfoMap;
        for (int c = 0; c < upsertParams.getNumConcurrency(); c++) {
            RateLimiter finalRateLimiter = rateLimiter;
            int finalC = c;
            String finalCollectionName = collectionName;
            Callable callable =
                    () -> {
                        log.info("线程[" + finalC + "]启动...");
                        UpsertResultItem upsertResultItem = new UpsertComp.UpsertResultItem();
                        List<Double> costTime = new ArrayList<>();
                        List<Integer> insertCnt = new ArrayList<>();
                        int retryCount = 0;
                        long lastPrintTime = System.currentTimeMillis();
                        LocalDateTime endRunningTime = LocalDateTime.now().plusMinutes(upsertParams.getRunningMinutes());
                        for (long r = ((upsertRounds / upsertParams.getNumConcurrency()) * finalC);
                             r < ((upsertRounds / upsertParams.getNumConcurrency()) * (finalC + 1));
                             r++) {
                            // 时间和数据量谁先到都结束
                            if (upsertParams.getRunningMinutes() > 0L && LocalDateTime.now().isAfter(endRunningTime)) {
                                log.info("线程[" + finalC + "] Upsert已到设定时长，停止插入...");
                                upsertResultItem.setUpsertCnt(insertCnt);
                                upsertResultItem.setCostTime(costTime);
                                return upsertResultItem;
                            }
                            // 3. QPS控制点（如果需要）
                            if (finalRateLimiter != null) {
                                finalRateLimiter.acquire(); // 阻塞直到获得令牌
                            }
                            // upsert 场景下必须显式传入 pk（即使 schema 是 autoID）
                            // partial update 场景下，仅生成主键 + updateFieldNames 中指定的字段
                            List<String> fieldsToGen = null;
                            if (upsertParams.isPartialUpdate() && upsertParams.getUpdateFieldNames() != null) {
                                fieldsToGen = upsertParams.getUpdateFieldNames().stream()
                                        .map(UpdateFieldName::getFieldName)
                                        .filter(fn -> fn != null && !fn.isEmpty())
                                        .collect(Collectors.toList());
                            }
                            List<JsonObject> jsonObjects = CommonFunction.genCommonData(upsertParams.getBatchSize(),
                                    (r * upsertParams.getBatchSize() + upsertParams.getStartId()), upsertParams.getGeneralDataRoleList(), upsertParams.getNumEntries(), upsertParams.getStartId(), describeCollectionResp, finalFieldDatasetInfoMap, upsertParams.getLengthFactor(), true,
                                    fieldsToGen, upsertParams.getNullableRatio());
                            // pkFromFilter 模式：用查到的真实 PK 覆盖生成的主键（全局行号取模循环复用）
                            if (finalPkPool != null) {
                                for (int i = 0; i < jsonObjects.size(); i++) {
                                    Object pk = finalPkPool.get((int) ((r * upsertParams.getBatchSize() + i) % finalPkPool.size()));
                                    JsonObject row = jsonObjects.get(i);
                                    if (pk instanceof Number) {
                                        row.addProperty(primaryFieldName, ((Number) pk).longValue());
                                    } else {
                                        row.addProperty(primaryFieldName, String.valueOf(pk));
                                    }
                                }
                            }
                            if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                                log.info("线程[" + finalC + "]导入数据 " + upsertParams.getBatchSize() + "条，范围: " + (r * upsertParams.getBatchSize() + upsertParams.getStartId()) + "~" + ((r + 1) * upsertParams.getBatchSize() + upsertParams.getStartId()));
                            }
                            UpsertResp upsertResp = null;
                            long startTime = System.currentTimeMillis();
                            try {
                                UpsertReq upsertReq = UpsertReq.builder()
                                        .data(jsonObjects)
                                        .collectionName(finalCollectionName)
                                        .partialUpdate(upsertParams.isPartialUpdate())
                                        .build();
                                if (upsertParams.getPartitionName() != null && !upsertParams.getPartitionName().equalsIgnoreCase("")) {
                                    upsertReq.setPartitionName(upsertParams.getPartitionName());
                                }
                                upsertResp = client.upsert(upsertReq);
                                if (upsertResp.getUpsertCnt() > 0) {
                                    retryCount = 0;
                                }
                            } catch (Exception e) {
                                statsReporter.recordFailure();
                                log.error("线程[" + finalC + "]" + "upsert error,reason:" + e.getMessage());
                                // 禁写后重试判断
                                if ((!upsertParams.isRetryAfterDeny()) || (retryCount == 10)) {
                                    upsertResultItem.setUpsertCnt(insertCnt);
                                    upsertResultItem.setCostTime(costTime);
                                    upsertResultItem.setExceptionMessage(e.getMessage());
                                    return upsertResultItem;
                                }
                                if (upsertParams.isRetryAfterDeny()) {
                                    retryCount++;
                                    log.info("线程[" + finalC + "]第" + retryCount + "次重试，原因:"
                                            + RetryLogUtil.retryReason(e) + "，等待30秒...");
                                    Thread.sleep(1000 * 30);
                                    continue;
                                }
                            }
                            long endTime = System.currentTimeMillis();
                            double costTimeItem = (endTime - startTime) / 1000.00;
                            costTime.add(costTimeItem);
                            statsReporter.recordCostTime((float) costTimeItem);
                            insertCnt.add((int) upsertResp.getUpsertCnt());
                            if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                                log.info(
                                        "线程 ["
                                                + finalC
                                                + "]Upsert第"
                                                + r
                                                + "批次数据, 成功upsert "
                                                + upsertResp.getUpsertCnt()
                                                + " 条， cost:"
                                                + (endTime - startTime) / 1000.00
                                                + " seconds ");
                                lastPrintTime = System.currentTimeMillis();
                            }
                        }
                        upsertResultItem.setUpsertCnt(insertCnt);
                        upsertResultItem.setCostTime(costTime);
                        return upsertResultItem;
                    };
            Future<UpsertComp.UpsertResultItem> future = executorService.submit(callable);
            list.add(future);

        }
        long requestNum = 0;
        double costTotal = 0.0;
        CommonResult commonResult;
        UpsertResult upsertResult = null;
        for (Future<UpsertComp.UpsertResultItem> future : list) {
            try {
                UpsertComp.UpsertResultItem upsertResultItem = future.get();
                long count = upsertResultItem.getUpsertCnt().stream().filter(x -> x != 0).count();
                double sum = upsertResultItem.getCostTime().stream().mapToDouble(Double::doubleValue).sum();
                log.info("线程返回结果[UpsertCnt]: " + upsertResultItem.getUpsertCnt());
                log.info("线程返回结果[CostTime]: " + upsertResultItem.getCostTime());
                requestNum += count;
                costTotal += sum;

            } catch (InterruptedException | ExecutionException e) {
                upsertResult = UpsertResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message(e.getMessage()).build())
                        .build();
                return upsertResult;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        upsertTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        // 查询实际导入数据量
        log.info(
                "Total cost of inserting " + requestNum * upsertParams.getBatchSize() + " entities: " + upsertTotalTime + " seconds!");
        log.info("Total insert " + requestNum + " 次数,RPS avg :" + requestNum / upsertTotalTime + " ");
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        // assertions
        List<String> assertMessages = new ArrayList<>();
        long totalEntries = requestNum * upsertParams.getBatchSize();
        if (requestNum == 0) {
            assertMessages.add("[ASSERT FAIL] upsert requestNum == 0, no data was upserted");
        }
        if (totalEntries == 0) {
            assertMessages.add("[ASSERT FAIL] upsert numEntries == 0");
        }
        // verifyPkPreserved：upsert 后用同一 filter 重查 PK 集合，与发送集合双向比对（验证 autoID upsert 保留主键）
        if (pkPool != null && !pkPool.isEmpty() && upsertParams.isVerifyPkPreserved()) {
            try {
                Set<String> sentPks = new HashSet<>();
                int used = (int) Math.min(upsertParams.getNumEntries(), pkPool.size());
                for (int i = 0; i < used; i++) {
                    sentPks.add(String.valueOf(pkPool.get(i)));
                }
                QueryResp afterResp = client.query(QueryReq.builder()
                        .collectionName(collectionName)
                        .outputFields(Collections.singletonList(primaryFieldName))
                        .filter(upsertParams.getPkFromFilter())
                        .consistencyLevel(ConsistencyLevel.STRONG)
                        .limit(16384)
                        .build());
                Set<String> afterPks = new HashSet<>();
                if (afterResp.getQueryResults() != null) {
                    for (QueryResp.QueryResult qr : afterResp.getQueryResults()) {
                        Object pk = qr.getEntity() == null ? null : qr.getEntity().get(primaryFieldName);
                        if (pk != null) {
                            afterPks.add(String.valueOf(pk));
                        }
                    }
                }
                Set<String> missing = new TreeSet<>(sentPks);
                missing.removeAll(afterPks);
                Set<String> unexpected = new TreeSet<>(afterPks);
                unexpected.removeAll(sentPks);
                if (missing.isEmpty() && unexpected.isEmpty()) {
                    assertMessages.add(String.format("[ASSERT PASS] upsert pkPreserved: %d/%d sent PKs preserved after upsert",
                            sentPks.size(), sentPks.size()));
                } else {
                    assertMessages.add(String.format("[ASSERT FAIL] upsert pkPreserved: %d/%d sent PKs missing, %d unexpected new PKs (missing sample: %s)",
                            missing.size(), sentPks.size(), unexpected.size(),
                            missing.stream().limit(10).collect(Collectors.toList())));
                }
            } catch (Exception e) {
                log.error("verifyPkPreserved 查询异常", e);
                assertMessages.add("[ASSERT FAIL] verifyPkPreserved query error: " + e.getMessage());
            }
        }
        if (!assertMessages.isEmpty()) {
            log.warn("Upsert assertions: " + assertMessages);
        }
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
        upsertResult = UpsertResult.builder()
                .commonResult(commonResult)
                .rps(requestNum / upsertTotalTime)
                .numEntries(totalEntries)
                .requestNum(requestNum)
                .costTime(upsertTotalTime)
                .assertMessages(assertMessages)
                .build();
        statsReporter.stop();
        executorService.shutdown();
        return upsertResult;
    }

    @Data
    public static class UpsertResultItem {
        private List<Double> costTime;
        private List<Integer> upsertCnt;
        private String exceptionMessage;
    }

    // 删除 index 之前的所有元素（保留 index 及之后的数据）
    public static <T> void removeBeforeIndex(List<T> list, int index) {
        if (index <= 0 || list.isEmpty()) return;

        // 计算实际删除数量（避免越界）
        int removeCount = Math.min(index, list.size());

        // 使用 subList 批量删除（O(1) 时间复杂度）
        list.subList(0, removeCount).clear();
    }
}
