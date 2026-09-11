package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateCollectionResult;
import custom.entity.result.ResultEnum;
import custom.utils.GenerateUtil;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import lombok.extern.slf4j.Slf4j;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import custom.utils.MathUtil;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CreateCollectionComp {
    /** 批量创建明细条数上限，超过则截断为失败明细，防止结果 JSON 过大上传失败 */
    private static final int MAX_DETAIL_ITEMS = 200;
    /** 截断时最多保留的失败明细条数 */
    private static final int MAX_FAILURE_ITEMS = 50;

    public static CreateCollectionResult createCollection(CreateCollectionParams createCollectionParams) {
        // 批量模式：createCount>1 且按前缀生成 collectionName+数字序号
        if (createCollectionParams.getCreateCount() > 1) {
            return createBatch(createCollectionParams);
        }
        String collection = null;
        CommonResult commonResult;
        try {
            collection = CommonFunction.genCommonCollection(resolveCollectionName(createCollectionParams),
                    createCollectionParams.isEnableDynamic(), createCollectionParams.getShardNum(), createCollectionParams.getNumPartitions(),
                    createCollectionParams.getFieldParamsList(), createCollectionParams.getFunctionParams(), createCollectionParams.getProperties()
                    , createCollectionParams.getDatabaseName());
            log.info("create collection [" + collection + "] success!");
            commonResult = CommonResult.builder()
                    .result(ResultEnum.SUCCESS.result)
                    .build();
        } catch (Exception e) {
            log.error("create collection failed!", e);
            commonResult = CommonResult.builder()
                    .result(ResultEnum.FAIL.result)
                    .message(e.getMessage())
                    .build();
            List<String> assertMessages = new ArrayList<>();
            assertMessages.add("[ASSERT FAIL] createCollection exception: " + e.getMessage());
            return CreateCollectionResult.builder()
                    .commonResult(commonResult)
                    .collectionName(null)
                    .assertMessages(assertMessages).build();
        }
        globalCollectionNames.add(collection);
        // 检查properties
        if (createCollectionParams.getProperties() != null && createCollectionParams.getProperties().size() > 0) {
            DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                    .collectionName(collection).build());
            Map<String, String> properties =
                    describeCollectionResp.getProperties();
            for (String s : properties.keySet()) {
                log.info(String.format("property %s : %s", s, properties.get(s)));
            }
        }
        // assertions
        List<String> assertMessages = new ArrayList<>();
        if (commonResult.getResult().equals(ResultEnum.FAIL.result)) {
            assertMessages.add("[ASSERT FAIL] createCollection exception: " + commonResult.getMessage());
        }
        if (collection == null || collection.isEmpty()) {
            assertMessages.add("[ASSERT FAIL] createCollection returned null/empty collectionName");
        }
        if (!assertMessages.isEmpty()) {
            log.warn("CreateCollection assertions: " + assertMessages);
        }
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
        return CreateCollectionResult.builder()
                .commonResult(commonResult)
                .collectionName(collection)
                .assertMessages(assertMessages).build();
    }

    /**
     * 批量并发创建：按 collectionName 前缀 + 7位数字序号（0000001 起）生成名称，
     * 起 min(numConcurrency, createCount) 个 worker，从共享游标抢任务，每个 collection 只被一个线程创建一次。
     */
    private static CreateCollectionResult createBatch(CreateCollectionParams params) {
        String prefix = params.getCollectionName();
        if (prefix == null || prefix.equalsIgnoreCase("")) {
            // 无前缀无法生成批量名称，退回带随机名的单个创建语义
            log.warn("批量创建需要 collectionName 作为前缀，当前为空，退回随机名单个创建");
            params.setCreateCount(1);
            return createCollection(params);
        }
        int createCount = params.getCreateCount();
        int workers = Math.min(Math.max(params.getNumConcurrency(), 1), createCount);
        long startTimeTotal = System.currentTimeMillis();
        log.info("Create 批量模式：前缀[{}]，共 {} 个 collection，{} 个 worker（请求并发度 {}）",
                prefix, createCount, workers, params.getNumConcurrency());

        AtomicInteger cursor = new AtomicInteger(1);
        CreateCollectionResult.CreateCollectionResultItem[] slotResults =
                new CreateCollectionResult.CreateCollectionResultItem[createCount];
        AtomicInteger threadIndex = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(workers,
                runnable -> new Thread(runnable, "create-worker-" + threadIndex.getAndIncrement()));
        try {
            for (int i = 0; i < workers; i++) {
                executorService.submit(() -> {
                    int count = 0;
                    int seq;
                    while ((seq = cursor.getAndIncrement()) <= createCount) {
                        slotResults[seq - 1] = createOne(params, prefix + String.format("%07d", seq));
                        count++;
                    }
                    log.info("线程[{}] 完成，共 create {} 个 collection", Thread.currentThread().getName(), count);
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Create 并发执行被中断: {}", e.getMessage());
        } finally {
            executorService.shutdownNow();
        }

        List<CreateCollectionResult.CreateCollectionResultItem> resultList = new ArrayList<>(createCount);
        for (int i = 0; i < createCount; i++) {
            CreateCollectionResult.CreateCollectionResultItem item = slotResults[i];
            if (item == null) {
                item = CreateCollectionResult.CreateCollectionResultItem.builder()
                        .collectionName(prefix + String.format("%07d", i + 1))
                        .costTime(-1)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message("create not executed (interrupted)").build())
                        .build();
            }
            resultList.add(item);
        }
        float totalCostTime = (float) ((System.currentTimeMillis() - startTimeTotal) / 1000.00);
        return buildBatchResult(resultList, prefix, totalCostTime);
    }

    private static CreateCollectionResult.CreateCollectionResultItem createOne(CreateCollectionParams params, String collectionName) {
        log.info("线程[" + Thread.currentThread().getName() + "] Create collection [" + collectionName + "]");
        long startTime = System.currentTimeMillis();
        try {
            String created = CommonFunction.genCommonCollection(collectionName,
                    params.isEnableDynamic(), params.getShardNum(), params.getNumPartitions(),
                    params.getFieldParamsList(), params.getFunctionParams(), params.getProperties(),
                    params.getDatabaseName());
            synchronized (globalCollectionNames) {
                globalCollectionNames.add(created);
            }
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.info("线程[" + Thread.currentThread().getName() + "] Create collection [" + created + "] 成功，cost: " + costTime + " s");
            return CreateCollectionResult.CreateCollectionResultItem.builder()
                    .collectionName(created)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                    .build();
        } catch (Exception e) {
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.warn("线程[" + Thread.currentThread().getName() + "] Create collection [" + collectionName + "] 失败: " + e.getMessage());
            return CreateCollectionResult.CreateCollectionResultItem.builder()
                    .collectionName(collectionName)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.FAIL.result)
                            .message(e.getMessage()).build())
                    .build();
        }
    }

    private static CreateCollectionResult buildBatchResult(
            List<CreateCollectionResult.CreateCollectionResultItem> resultList, String prefix, float totalCostTime) {
        int totalCount = resultList.size();
        int failCount = (int) resultList.stream()
                .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                .count();
        int successCount = totalCount - failCount;
        // 延迟统计：基于实际执行的 create 耗时（占位项 costTime=-1 不计入）
        List<Float> costTimeTotal = resultList.stream()
                .filter(item -> item.getCostTime() >= 0)
                .map(CreateCollectionResult.CreateCollectionResultItem::getCostTime)
                .collect(Collectors.toList());
        List<String> assertMessages = resultList.stream()
                .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                .map(item -> "[ASSERT FAIL] createCollection [" + item.getCollectionName() + "] failed: "
                        + item.getCommonResult().getMessage())
                .collect(Collectors.toList());
        if (!assertMessages.isEmpty()) {
            log.warn("CreateCollection assertions: " + assertMessages);
        }
        boolean truncated = false;
        if (totalCount > MAX_DETAIL_ITEMS) {
            truncated = true;
            resultList = resultList.stream()
                    .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                    .limit(MAX_FAILURE_ITEMS)
                    .collect(Collectors.toList());
            log.info("Create 结果明细过大（{} 条），截断为 {} 条失败明细，总数统计: total={}, success={}, fail={}",
                    totalCount, resultList.size(), totalCount, successCount, failCount);
        }
        CommonResult commonResult = CommonResult.builder()
                .result(failCount == 0 ? ResultEnum.SUCCESS.result : (successCount == 0 ? ResultEnum.FAIL.result : ResultEnum.WARNING.result))
                .message(failCount == 0 ? "" : failCount + "/" + totalCount + " collections create failed")
                .build();
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
        return CreateCollectionResult.builder()
                .commonResult(commonResult)
                .collectionName(prefix + "*")
                .assertMessages(assertMessages)
                .createCollectionResultList(resultList)
                .totalCount(totalCount)
                .successCount(successCount)
                .failCount(failCount)
                .truncated(truncated)
                .totalCostTime(totalCostTime)
                .rps(totalCostTime > 0 ? successCount / totalCostTime : 0)
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .build();
    }

    private static String resolveCollectionName(CreateCollectionParams params) {
        String collectionName = params.getCollectionName();
        if (collectionName == null || collectionName.equals("")) {
            return collectionName;
        }
        if (!params.isCollectionNameUsePrefix()) {
            return collectionName;
        }
        return collectionName + GenerateUtil.getRandomString(10);
    }

}
