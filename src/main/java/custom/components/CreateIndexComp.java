package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateIndexParams;
import custom.utils.MathUtil;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateIndexResult;
import custom.entity.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static custom.BaseTest.globalCollectionNames;

@Slf4j
public class CreateIndexComp {
    /** 多 collection 模式明细条数上限，超过则截断为失败明细，防止结果 JSON 过大上传失败 */
    private static final int MAX_DETAIL_ITEMS = 200;
    /** 截断时最多保留的失败明细条数 */
    private static final int MAX_FAILURE_ITEMS = 50;

    public static CreateIndexResult CreateIndex(CreateIndexParams createIndexParams) {
        boolean multiMode = (createIndexParams.getCollectionNamePrefix() != null
                && !createIndexParams.getCollectionNamePrefix().equalsIgnoreCase(""))
                || createIndexParams.getCollectionRangeStart() >= 0;
        if (multiMode) {
            return createIndexMulti(createIndexParams);
        }
        String collectionName = (createIndexParams.getCollectionName() == null || createIndexParams.getCollectionName().equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : createIndexParams.getCollectionName();
        CommonResult commonResult;
        CreateIndexResult createIndexResult = CreateIndexResult.builder().build();
        String databaseName = "";
        if (createIndexParams.getDatabaseName() != null && !createIndexParams.getDatabaseName().equalsIgnoreCase("")) {
            databaseName = createIndexParams.getDatabaseName();
        }
        try {
            long startTimeTotal = System.currentTimeMillis();
            CommonFunction.createCommonIndex(collectionName, createIndexParams.getIndexParams(), databaseName);
            long endTimeTotal = System.currentTimeMillis();
            float indexCost = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
            commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
            createIndexResult.setCostTimes(indexCost);
        } catch (Exception e) {
            log.error(e.getMessage());
            commonResult = CommonResult.builder().result(ResultEnum.FAIL.result)
                    .message(e.getMessage()).build();
        }
        // assertions
        List<String> assertMessages = new ArrayList<>();
        if (commonResult.getResult().equals(ResultEnum.FAIL.result)) {
            assertMessages.add("[ASSERT FAIL] createIndex exception: " + commonResult.getMessage());
        }
        if (commonResult.getResult().equals(ResultEnum.SUCCESS.result) && createIndexResult.getCostTimes() <= 0) {
            assertMessages.add("[ASSERT WARN] createIndex costTimes <= 0");
        }
        if (!assertMessages.isEmpty()) {
            log.warn("CreateIndex assertions: " + assertMessages);
        }
        createIndexResult.setIndexParams(createIndexParams.getIndexParams());
        createIndexResult.setCommonResult(commonResult);
        createIndexResult.setCollectionName(collectionName);
        createIndexResult.setAssertMessages(assertMessages);
        return createIndexResult;
    }

    /**
     * 多 collection 模式：按前缀+区间从 globalCollectionNames 池子解析目标列表，
     * numConcurrency>1 时起 worker 并发，每个 collection 只被一个线程建索引一次。
     */
    private static CreateIndexResult createIndexMulti(CreateIndexParams params) {
        List<String> targetCollections = CommonFunction.filterCollectionPool(globalCollectionNames,
                params.getCollectionNamePrefix(), params.getCollectionRangeStart(), params.getCollectionRangeEnd());
        String databaseName = params.getDatabaseName() == null ? "" : params.getDatabaseName();
        int numConcurrency = Math.max(params.getNumConcurrency(), 1);
        log.info("CreateIndex 多 collection 模式：共 {} 个，并发度 {}", targetCollections.size(),
                Math.min(numConcurrency, targetCollections.size()));

        long startTimeTotal = System.currentTimeMillis();
        CreateIndexResult.CreateIndexResultItem[] slotResults =
                new CreateIndexResult.CreateIndexResultItem[targetCollections.size()];
        if (numConcurrency <= 1 || targetCollections.size() <= 1) {
            for (int i = 0; i < targetCollections.size(); i++) {
                slotResults[i] = createIndexOne(targetCollections.get(i), params, databaseName);
            }
        } else {
            int workers = Math.min(numConcurrency, targetCollections.size());
            AtomicInteger cursor = new AtomicInteger(0);
            AtomicInteger threadIndex = new AtomicInteger(0);
            ExecutorService executorService = Executors.newFixedThreadPool(workers,
                    runnable -> new Thread(runnable, "createindex-worker-" + threadIndex.getAndIncrement()));
            try {
                for (int i = 0; i < workers; i++) {
                    executorService.submit(() -> {
                        int count = 0;
                        int idx;
                        while ((idx = cursor.getAndIncrement()) < targetCollections.size()) {
                            slotResults[idx] = createIndexOne(targetCollections.get(idx), params, databaseName);
                            count++;
                        }
                        log.info("线程[{}] 完成，共 createIndex {} 个 collection", Thread.currentThread().getName(), count);
                    });
                }
                executorService.shutdown();
                executorService.awaitTermination(2, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("CreateIndex 并发执行被中断: {}", e.getMessage());
            } finally {
                executorService.shutdownNow();
            }
        }
        float totalCost = (float) ((System.currentTimeMillis() - startTimeTotal) / 1000.00);

        List<CreateIndexResult.CreateIndexResultItem> resultList = new ArrayList<>(targetCollections.size());
        for (int i = 0; i < targetCollections.size(); i++) {
            CreateIndexResult.CreateIndexResultItem item = slotResults[i];
            if (item == null) {
                item = CreateIndexResult.CreateIndexResultItem.builder()
                        .collectionName(targetCollections.get(i))
                        .costTime(-1)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message("createIndex not executed (interrupted)").build())
                        .build();
            }
            resultList.add(item);
        }
        return buildMultiResult(resultList, params, totalCost);
    }

    private static CreateIndexResult.CreateIndexResultItem createIndexOne(String collectionName,
                                                                          CreateIndexParams params, String databaseName) {
        log.info("线程[" + Thread.currentThread().getName() + "] CreateIndex collection [" + collectionName + "]");
        long startTime = System.currentTimeMillis();
        try {
            CommonFunction.createCommonIndex(collectionName, params.getIndexParams(), databaseName);
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.info("线程[" + Thread.currentThread().getName() + "] CreateIndex collection [" + collectionName + "] 成功，cost: " + costTime + " s");
            return CreateIndexResult.CreateIndexResultItem.builder()
                    .collectionName(collectionName)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                    .build();
        } catch (Exception e) {
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.warn("线程[" + Thread.currentThread().getName() + "] CreateIndex collection [" + collectionName + "] 失败: " + e.getMessage());
            return CreateIndexResult.CreateIndexResultItem.builder()
                    .collectionName(collectionName)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.FAIL.result)
                            .message(e.getMessage()).build())
                    .build();
        }
    }

    private static CreateIndexResult buildMultiResult(List<CreateIndexResult.CreateIndexResultItem> resultList,
                                                      CreateIndexParams params, float totalCost) {
        int totalCount = resultList.size();
        int failCount = (int) resultList.stream()
                .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                .count();
        int successCount = totalCount - failCount;
        // 延迟统计：基于实际执行的建索引耗时（占位项 costTime=-1 不计入）
        List<Float> costTimeTotal = resultList.stream()
                .filter(item -> item.getCostTime() >= 0)
                .map(CreateIndexResult.CreateIndexResultItem::getCostTime)
                .collect(Collectors.toList());
        List<String> assertMessages = resultList.stream()
                .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                .map(item -> "[ASSERT FAIL] createIndex [" + item.getCollectionName() + "] failed: "
                        + item.getCommonResult().getMessage())
                .collect(Collectors.toList());
        if (!assertMessages.isEmpty()) {
            log.warn("CreateIndex assertions: " + assertMessages);
        }
        boolean truncated = false;
        if (totalCount > MAX_DETAIL_ITEMS) {
            truncated = true;
            resultList = resultList.stream()
                    .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                    .limit(MAX_FAILURE_ITEMS)
                    .collect(Collectors.toList());
            log.info("CreateIndex 结果明细过大（{} 条），截断为 {} 条失败明细，总数统计: total={}, success={}, fail={}",
                    totalCount, resultList.size(), totalCount, successCount, failCount);
        }
        CommonResult commonResult = CommonResult.builder()
                .result(failCount == 0 ? ResultEnum.SUCCESS.result : (successCount == 0 ? ResultEnum.FAIL.result : ResultEnum.WARNING.result))
                .message(failCount == 0 ? "" : failCount + "/" + totalCount + " collections createIndex failed")
                .build();
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
        String prefix = params.getCollectionNamePrefix() != null ? params.getCollectionNamePrefix() : "";
        return CreateIndexResult.builder()
                .commonResult(commonResult)
                .collectionName(prefix + "*")
                .indexParams(params.getIndexParams())
                .costTimes(totalCost)
                .assertMessages(assertMessages)
                .createIndexResultList(resultList)
                .totalCount(totalCount)
                .successCount(successCount)
                .failCount(failCount)
                .truncated(truncated)
                .totalCostTime(totalCost)
                .rps(totalCost > 0 ? successCount / totalCost : 0)
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .build();
    }
}
