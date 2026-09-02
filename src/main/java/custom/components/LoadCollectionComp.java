package custom.components;

import custom.common.CommonFunction;
import custom.entity.LoadParams;
import custom.entity.result.CommonResult;
import custom.entity.result.LoadResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class LoadCollectionComp {
    private static final long LOAD_COLLECTION_TIMEOUT_MS = 30 * 60 * 1000L;

    public static LoadResult loadCollection(LoadParams loadParams) {
        List<LoadResult.LoadResultItem> loadResultList = new ArrayList<>();

        for (String collectionName : resolveTargetCollections(loadParams)) {
            loadResultList.add(loadOne(collectionName, loadParams));
        }

        // assertions
        List<String> assertMessages = new ArrayList<>();
        if (loadResultList.isEmpty()) {
            assertMessages.add("[ASSERT FAIL] loadCollection: no collection was loaded");
        }
        for (LoadResult.LoadResultItem item : loadResultList) {
            if (item.getCommonResult().getResult().equals(ResultEnum.FAIL.result)) {
                assertMessages.add("[ASSERT FAIL] loadCollection [" + item.getCollectionName() + "] failed: " + item.getCommonResult().getMessage());
            }
        }
        if (!assertMessages.isEmpty()) {
            log.warn("LoadCollection assertions: " + assertMessages);
        }
        return LoadResult.builder().loadResultList(loadResultList).assertMessages(assertMessages).build();
    }

    /**
     * 解析本次要 load 的 collection 列表。
     * 设置了 collectionNamePrefix 或 collectionRangeStart>=0 时进入多 collection 模式：
     * 目标集合 = loadAll ? 实例全量列表 : globalCollectionNames 池子，再按前缀+区间过滤。
     */
    private static List<String> resolveTargetCollections(LoadParams loadParams) {
        boolean multiMode = (loadParams.getCollectionNamePrefix() != null && !loadParams.getCollectionNamePrefix().equalsIgnoreCase(""))
                || loadParams.getCollectionRangeStart() >= 0;
        if (multiMode) {
            List<String> source = loadParams.isLoadAll()
                    ? milvusClientV2.listCollections().getCollectionNames()
                    : globalCollectionNames;
            List<String> target = CommonFunction.filterCollectionPool(source,
                    loadParams.getCollectionNamePrefix(),
                    loadParams.getCollectionRangeStart(),
                    loadParams.getCollectionRangeEnd());
            log.info("Load 多 collection 模式：共 {} 个 collection 将被 load", target.size());
            return target;
        }
        if (loadParams.isLoadAll()) {
            log.info("load all collection !");
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            return listCollectionsResp.getCollectionNames();
        }
        String collectionName = (loadParams.getCollectionName() == null || loadParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1) : loadParams.getCollectionName();
        return Collections.singletonList(collectionName);
    }

    private static LoadResult.LoadResultItem loadOne(String collectionName, LoadParams loadParams) {
        try {
            log.info("Loading collection [" + collectionName + "]");
            long startLoadTime = System.currentTimeMillis();
            boolean loadState;
            LoadCollectionReq.LoadCollectionReqBuilder reqBuilder = LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .skipLoadDynamicField(loadParams.isSkipLoadDynamicField())
                    .async(false)
                    .timeout(LOAD_COLLECTION_TIMEOUT_MS);
            if (loadParams.getReplicaNum() > 0) {
                reqBuilder.numReplicas(loadParams.getReplicaNum());
            }
            LoadCollectionReq collectionReq = reqBuilder.build();
            if (loadParams.getLoadFields() != null && loadParams.getLoadFields().size() > 0) {
                collectionReq.setLoadFields(loadParams.getLoadFields());
            }
            milvusClientV2.loadCollection(collectionReq);
            do {
                loadState = milvusClientV2.getLoadState(GetLoadStateReq.builder()
                        .collectionName(collectionName).build());
                log.info("轮询load结果：" + loadState);
                Thread.sleep(1000L);
            } while (!loadState);
            long endLoadTime = System.currentTimeMillis();
            log.info("Load collection [" + collectionName + "] cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
            return LoadResult.LoadResultItem.builder()
                    .collectionName(collectionName)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build())
                    .costTimes((endLoadTime - startLoadTime) / 1000.00)
                    .build();
        } catch (Exception e) {
            log.error("load [" + collectionName + "] failed! reason:" + e.getMessage());
            return LoadResult.LoadResultItem.builder()
                    .collectionName(collectionName)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.FAIL.result)
                            .message(e.getMessage()).build())
                    .build();
        }
    }
}
