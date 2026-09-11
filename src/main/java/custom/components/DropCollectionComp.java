package custom.components;

import custom.common.CommonFunction;
import custom.entity.DropCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DropCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.ListCollectionsReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.utility.request.DropAliasReq;
import io.milvus.v2.service.utility.request.ListAliasesReq;
import io.milvus.v2.service.utility.response.ListAliasResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import custom.utils.MathUtil;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DropCollectionComp {
    /** 结果明细条数上限，超过则截断为失败明细，防止结果 JSON 过大上传失败 */
    private static final int MAX_DETAIL_ITEMS = 200;
    /** 截断时最多保留的失败明细条数 */
    private static final int MAX_FAILURE_ITEMS = 50;

    public static DropCollectionResult dropCollection(DropCollectionParams dropCollectionParams) {
        List<DropCollectionResult.DropCollectionResultItem> dropCollectionResultList;
        if (dropCollectionParams.isCollectionNameUsePrefix()
                && dropCollectionParams.getCollectionName() != null
                && !dropCollectionParams.getCollectionName().equalsIgnoreCase("")) {
            List<String> collectionNames = collectionNamesByPrefix(dropCollectionParams.getCollectionName(), dropCollectionParams.getDatabaseName());
            log.info("Drop collections by prefix [{}], dropAll [{}]: {}", dropCollectionParams.getCollectionName(), dropCollectionParams.isDropAll(), CommonFunction.summarizeForLog(collectionNames));
            if (collectionNames.isEmpty()) {
                dropCollectionResultList = new ArrayList<>();
                dropCollectionResultList.add(DropCollectionResult.DropCollectionResultItem.builder()
                        .collectionName(dropCollectionParams.getCollectionName())
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.FAIL.result)
                                .message("no collection matched prefix: " + dropCollectionParams.getCollectionName())
                                .build())
                        .build());
            } else if (dropCollectionParams.isDropAll()) {
                dropCollectionResultList = dropBatch(collectionNames, dropCollectionParams);
            } else {
                String collectionName = collectionNames.get(collectionNames.size() - 1);
                dropCollectionResultList = new ArrayList<>();
                dropCollectionResultList.add(dropOneCollection(collectionName, dropCollectionParams.getDatabaseName()));
            }
        } else if (dropCollectionParams.isDropAll()) {
            List<String> collectionNames = listCollectionNames(dropCollectionParams.getDatabaseName());
            log.info("Drop all collections: " + CommonFunction.summarizeForLog(collectionNames));
            dropCollectionResultList = dropBatch(collectionNames, dropCollectionParams);
        } else {
            String collectionName = (dropCollectionParams.getCollectionName() == null || dropCollectionParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(globalCollectionNames.size() - 1) : dropCollectionParams.getCollectionName();
            dropCollectionResultList = new ArrayList<>();
            dropCollectionResultList.add(dropOneCollection(collectionName, dropCollectionParams.getDatabaseName()));
        }
        // assertions
        List<String> assertMessages = new ArrayList<>();
        for (DropCollectionResult.DropCollectionResultItem item : dropCollectionResultList) {
            if (item.getCommonResult().getResult().equals(ResultEnum.FAIL.result)) {
                assertMessages.add("[ASSERT FAIL] dropCollection [" + item.getCollectionName() + "] failed: " + item.getCommonResult().getMessage());
            }
        }
        if (!assertMessages.isEmpty()) {
            log.warn("DropCollection assertions: " + assertMessages);
        }
        int totalCount = dropCollectionResultList.size();
        int failCount = (int) dropCollectionResultList.stream()
                .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                .count();
        // 延迟统计：基于实际执行的 drop 耗时（占位项 costTime=-1 不计入）
        List<Float> costTimeTotal = dropCollectionResultList.stream()
                .filter(item -> item.getCostTime() >= 0)
                .map(DropCollectionResult.DropCollectionResultItem::getCostTime)
                .collect(Collectors.toList());
        boolean truncated = false;
        // collection 太多时全量明细会导致结果 JSON 过大、上传 QTP 失败，
        // 只保留部分失败明细，总数看 totalCount/successCount/failCount
        if (totalCount > MAX_DETAIL_ITEMS) {
            truncated = true;
            dropCollectionResultList = dropCollectionResultList.stream()
                    .filter(item -> !ResultEnum.SUCCESS.result.equals(item.getCommonResult().getResult()))
                    .limit(MAX_FAILURE_ITEMS)
                    .collect(Collectors.toList());
            log.info("Drop 结果明细过大（{} 条），截断为 {} 条失败明细，总数统计: total={}, success={}, fail={}",
                    totalCount, dropCollectionResultList.size(), totalCount, totalCount - failCount, failCount);
        }
        return DropCollectionResult.builder()
                .dropCollectionResultList(dropCollectionResultList)
                .assertMessages(assertMessages)
                .totalCount(totalCount)
                .successCount(totalCount - failCount)
                .failCount(failCount)
                .truncated(truncated)
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .build();
    }

    /**
     * 批量删除：numConcurrency>1 时起 min(并发数, 待删数) 个 worker，从共享游标抢任务，
     * 每个 collection 只被一个线程删除一次；结果按目标列表原始顺序返回。
     */
    private static List<DropCollectionResult.DropCollectionResultItem> dropBatch(List<String> collectionNames,
                                                                                 DropCollectionParams params) {
        int numConcurrency = Math.max(params.getNumConcurrency(), 1);
        if (numConcurrency <= 1 || collectionNames.size() <= 1) {
            List<DropCollectionResult.DropCollectionResultItem> list = new ArrayList<>();
            for (String collectionName : collectionNames) {
                list.add(dropOneCollection(collectionName, params.getDatabaseName()));
            }
            return list;
        }
        int workers = Math.min(numConcurrency, collectionNames.size());
        log.info("Drop 并发模式：{} 个 collection，{} 个 worker（请求并发度 {}）",
                collectionNames.size(), workers, numConcurrency);
        AtomicInteger cursor = new AtomicInteger(0);
        DropCollectionResult.DropCollectionResultItem[] slotResults =
                new DropCollectionResult.DropCollectionResultItem[collectionNames.size()];
        AtomicInteger threadIndex = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(workers,
                runnable -> new Thread(runnable, "drop-worker-" + threadIndex.getAndIncrement()));
        try {
            for (int i = 0; i < workers; i++) {
                executorService.submit(() -> {
                    int count = 0;
                    int idx;
                    while ((idx = cursor.getAndIncrement()) < collectionNames.size()) {
                        slotResults[idx] = dropOneCollection(collectionNames.get(idx), params.getDatabaseName());
                        count++;
                    }
                    log.info("线程[{}] 完成，共 drop {} 个 collection", Thread.currentThread().getName(), count);
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Drop 并发执行被中断: {}", e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
        List<DropCollectionResult.DropCollectionResultItem> list = new ArrayList<>(collectionNames.size());
        for (int i = 0; i < collectionNames.size(); i++) {
            DropCollectionResult.DropCollectionResultItem item = slotResults[i];
            if (item == null) {
                item = DropCollectionResult.DropCollectionResultItem.builder()
                        .collectionName(collectionNames.get(i))
                        .costTime(-1)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message("drop not executed (interrupted)").build())
                        .build();
            }
            list.add(item);
        }
        return list;
    }

    private static List<String> collectionNamesByPrefix(String prefix, String databaseName) {
        List<String> collectionNames = listCollectionNames(databaseName);
        List<String> matched = new ArrayList<>();
        for (String collectionName : collectionNames) {
            if (collectionName != null && collectionName.startsWith(prefix)) {
                matched.add(collectionName);
            }
        }
        return matched;
    }

    private static List<String> listCollectionNames(String databaseName) {
        ListCollectionsResp listCollectionsResp;
        if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
            listCollectionsResp = milvusClientV2.listCollectionsV2(ListCollectionsReq.builder()
                    .databaseName(databaseName)
                    .build());
        } else {
            listCollectionsResp = milvusClientV2.listCollections();
        }
        return listCollectionsResp.getCollectionNames();
    }

    private static DropCollectionResult.DropCollectionResultItem dropOneCollection(String collectionName, String databaseName) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("线程[" + Thread.currentThread().getName() + "] Drop collection: " + collectionName);
            dropAliasesForCollection(collectionName, databaseName);
            DropCollectionReq dropCollectionReq = DropCollectionReq.builder()
                    .collectionName(collectionName).build();
            if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
                dropCollectionReq.setDatabaseName(databaseName);
            }
            milvusClientV2.dropCollection(dropCollectionReq);
            synchronized (globalCollectionNames) {
                globalCollectionNames.remove(collectionName);
            }
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.info("线程[" + Thread.currentThread().getName() + "] Drop collection [" + collectionName + "] 成功，cost: " + costTime + " s");
            return DropCollectionResult.DropCollectionResultItem.builder()
                    .collectionName(collectionName)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.warn("线程[" + Thread.currentThread().getName() + "] Drop collection [" + collectionName + "] 失败: " + e.getMessage());
            return DropCollectionResult.DropCollectionResultItem.builder()
                    .collectionName(collectionName)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.FAIL.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }

    /**
     * 删除 collection 关联的所有 alias
     */
    private static void dropAliasesForCollection(String collectionName, String databaseName) {
        try {
            ListAliasesReq.ListAliasesReqBuilder builder = ListAliasesReq.builder().collectionName(collectionName);
            if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
                builder.databaseName(databaseName);
            }
            ListAliasResp listAliasResp = milvusClientV2.listAliases(builder.build());
            List<String> aliases = listAliasResp.getAlias();
            if (aliases != null && !aliases.isEmpty()) {
                log.info("Collection [{}] has aliases: {}, dropping them first", collectionName, aliases);
                for (String alias : aliases) {
                    DropAliasReq dropAliasReq = DropAliasReq.builder().alias(alias).build();
                    if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
                        dropAliasReq.setDatabaseName(databaseName);
                    }
                    milvusClientV2.dropAlias(dropAliasReq);
                    log.info("Dropped alias [{}] for collection [{}]", alias, collectionName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to drop aliases for collection [{}]: {}", collectionName, e.getMessage());
        }
    }
}
