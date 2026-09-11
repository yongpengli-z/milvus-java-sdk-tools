package custom.components;

import custom.common.CommonFunction;
import custom.entity.ReleaseParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ReleaseResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
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
public class ReleaseCollectionComp {
    /** 结果明细条数上限，超过则截断为失败明细，防止结果 JSON 过大上传失败 */
    private static final int MAX_DETAIL_ITEMS = 200;
    /** 截断时最多保留的失败明细条数 */
    private static final int MAX_FAILURE_ITEMS = 50;

    public static ReleaseResult releaseCollection(ReleaseParams releaseParams) {
        long startTimeTotal = System.currentTimeMillis();
        List<String> targetCollections = resolveTargetCollections(releaseParams);
        int numConcurrency = Math.max(releaseParams.getNumConcurrency(), 1);
        List<ReleaseResult.ReleaseResultItem> releaseResultList;
        if (numConcurrency <= 1 || targetCollections.size() <= 1) {
            // 串行（默认，兼容旧行为）
            releaseResultList = new ArrayList<>();
            for (String collectionName : targetCollections) {
                releaseResultList.add(releaseOne(collectionName));
            }
        } else {
            releaseResultList = releaseConcurrently(targetCollections, numConcurrency);
        }

        // assertions
        List<String> assertMessages = new ArrayList<>();
        for (ReleaseResult.ReleaseResultItem item : releaseResultList) {
            if (item.getCommonResult().getResult().equals(ResultEnum.EXCEPTION.result)) {
                assertMessages.add("[ASSERT FAIL] releaseCollection [" + item.getCollectionName() + "] failed: " + item.getCommonResult().getMessage());
            }
        }
        if (!assertMessages.isEmpty()) {
            log.warn("ReleaseCollection assertions: " + assertMessages);
        }

        int totalCount = releaseResultList.size();
        int failCount = (int) releaseResultList.stream()
                .filter(item -> item.getCommonResult().getResult().equals(ResultEnum.EXCEPTION.result))
                .count();
        int successCount = totalCount - failCount;
        // 延迟统计：基于实际执行的 release 耗时（占位项 costTime=-1 不计入）
        List<Float> costTimeTotal = releaseResultList.stream()
                .filter(item -> item.getCostTime() >= 0)
                .map(ReleaseResult.ReleaseResultItem::getCostTime)
                .collect(Collectors.toList());
        boolean truncated = false;
        // collection 太多时全量明细会导致结果 JSON 过大、上传 QTP 失败，
        // 只保留部分失败明细，总数看 totalCount/successCount/failCount
        if (totalCount > MAX_DETAIL_ITEMS) {
            truncated = true;
            releaseResultList = releaseResultList.stream()
                    .filter(item -> item.getCommonResult().getResult().equals(ResultEnum.EXCEPTION.result))
                    .limit(MAX_FAILURE_ITEMS)
                    .collect(Collectors.toList());
            log.info("Release 结果明细过大（{} 条），截断为 {} 条失败明细，总数统计: total={}, success={}, fail={}",
                    totalCount, releaseResultList.size(), totalCount, successCount, failCount);
        }
        float totalCostTime = (float) ((System.currentTimeMillis() - startTimeTotal) / 1000.00);
        // rps = 每秒成功 release 数（与 search 口径一致，只计成功请求）
        double rps = totalCostTime > 0 ? successCount / totalCostTime : 0;
        return ReleaseResult.builder()
                .releaseResultList(releaseResultList)
                .assertMessages(assertMessages)
                .totalCount(totalCount)
                .successCount(successCount)
                .failCount(failCount)
                .truncated(truncated)
                .totalCostTime(totalCostTime)
                .rps(rps)
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
     * 解析本次要 release 的 collection 列表。
     * 设置了 collectionNamePrefix 或 collectionRangeStart>=0 时进入多 collection 模式：
     * 目标集合 = releaseAll ? 实例全量列表 : globalCollectionNames 池子，再按前缀+区间过滤。
     */
    private static List<String> resolveTargetCollections(ReleaseParams releaseParams) {
        boolean multiMode = (releaseParams.getCollectionNamePrefix() != null && !releaseParams.getCollectionNamePrefix().equalsIgnoreCase(""))
                || releaseParams.getCollectionRangeStart() >= 0;
        if (multiMode) {
            List<String> source = releaseParams.isReleaseAll()
                    ? milvusClientV2.listCollections().getCollectionNames()
                    : globalCollectionNames;
            List<String> target = CommonFunction.filterCollectionPool(source,
                    releaseParams.getCollectionNamePrefix(),
                    releaseParams.getCollectionRangeStart(),
                    releaseParams.getCollectionRangeEnd());
            log.info("Release 多 collection 模式：共 {} 个 collection 将被 release", target.size());
            return target;
        }
        if (releaseParams.isReleaseAll()) {
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            log.info("Release all collections: " + CommonFunction.summarizeForLog(collectionNames));
            return collectionNames;
        }
        String collectionName = (releaseParams.getCollectionName() == null || releaseParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1) : releaseParams.getCollectionName();
        return Collections.singletonList(collectionName);
    }

    /**
     * 并发 release：起 min(numConcurrency, collection数) 个 worker 线程，
     * 所有 worker 从共享游标抢占下一个 collection，每个 collection 只被一个线程 release 一次，不重复、不漏。
     * 结果按目标列表原始顺序返回，保证与串行模式的输出一一对应。
     */
    private static List<ReleaseResult.ReleaseResultItem> releaseConcurrently(List<String> targetCollections, int numConcurrency) {
        int workers = Math.min(numConcurrency, targetCollections.size());
        log.info("Release 并发模式：{} 个 collection，{} 个 worker（请求并发度 {}）",
                targetCollections.size(), workers, numConcurrency);
        AtomicInteger cursor = new AtomicInteger(0);
        // 按下标占位，保证结果顺序与目标列表一致
        ReleaseResult.ReleaseResultItem[] slotResults = new ReleaseResult.ReleaseResultItem[targetCollections.size()];
        AtomicInteger threadIndex = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(workers,
                runnable -> new Thread(runnable, "release-worker-" + threadIndex.getAndIncrement()));
        try {
            for (int i = 0; i < workers; i++) {
                executorService.submit(() -> {
                    int count = 0;
                    int idx;
                    while ((idx = cursor.getAndIncrement()) < targetCollections.size()) {
                        slotResults[idx] = releaseOne(targetCollections.get(idx));
                        count++;
                    }
                    log.info("线程[{}] 完成，共 release {} 个 collection", Thread.currentThread().getName(), count);
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Release 并发执行被中断: {}", e.getMessage());
        } finally {
            executorService.shutdownNow();
        }
        List<ReleaseResult.ReleaseResultItem> releaseResultList = new ArrayList<>(targetCollections.size());
        for (int i = 0; i < targetCollections.size(); i++) {
            ReleaseResult.ReleaseResultItem item = slotResults[i];
            if (item == null) {
                // 仅在被中断时可能出现：占位为未执行，costTime=-1 不计入延迟统计
                item = ReleaseResult.ReleaseResultItem.builder()
                        .collectionName(targetCollections.get(i))
                        .costTime(-1)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message("release not executed (interrupted)").build())
                        .build();
            }
            releaseResultList.add(item);
        }
        return releaseResultList;
    }

    private static ReleaseResult.ReleaseResultItem releaseOne(String collectionName) {
        log.info("线程[" + Thread.currentThread().getName() + "] Release collection [" + collectionName + "]");
        long startTime = System.currentTimeMillis();
        try {
            milvusClientV2.releaseCollection(ReleaseCollectionReq.builder()
                    .collectionName(collectionName).build());
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.info("线程[" + Thread.currentThread().getName() + "] Release collection [" + collectionName + "] 成功，cost: " + costTime + " s");
            return ReleaseResult.ReleaseResultItem.builder()
                    .collectionName(collectionName)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build()).build();
        } catch (Exception e) {
            float costTime = (float) ((System.currentTimeMillis() - startTime) / 1000.00);
            log.warn("线程[" + Thread.currentThread().getName() + "] Release collection [" + collectionName + "] 失败: " + e.getMessage());
            return ReleaseResult.ReleaseResultItem.builder()
                    .collectionName(collectionName)
                    .costTime(costTime)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage()).build())
                    .build();
        }
    }
}
