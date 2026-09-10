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

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ReleaseCollectionComp {
    public static ReleaseResult releaseCollection(ReleaseParams releaseParams) {
        List<String> targetCollections = resolveTargetCollections(releaseParams);
        int numConcurrency = Math.min(Math.max(releaseParams.getNumConcurrency(), 1), 64);
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
        return ReleaseResult.builder().releaseResultList(releaseResultList).assertMessages(assertMessages).build();
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
                // 仅在被中断时可能出现：占位为未执行
                item = ReleaseResult.ReleaseResultItem.builder()
                        .collectionName(targetCollections.get(i))
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
        try {
            milvusClientV2.releaseCollection(ReleaseCollectionReq.builder()
                    .collectionName(collectionName).build());
            log.info("线程[" + Thread.currentThread().getName() + "] Release collection [" + collectionName + "] 成功");
            return ReleaseResult.ReleaseResultItem.builder()
                    .collectionName(collectionName)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build()).build();
        } catch (Exception e) {
            log.warn("线程[" + Thread.currentThread().getName() + "] Release collection [" + collectionName + "] 失败: " + e.getMessage());
            return ReleaseResult.ReleaseResultItem.builder()
                    .collectionName(collectionName)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage()).build())
                    .build();
        }
    }
}
