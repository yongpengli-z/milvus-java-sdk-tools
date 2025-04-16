package custom.components;

import custom.entity.LoadParams;
import custom.entity.result.CommonResult;
import custom.entity.result.LoadResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class LoadCollectionComp {
    public static LoadResult loadCollection(LoadParams loadParams) {
        List<LoadResult.LoadResultItem> loadResultList = new ArrayList<>();

        if (loadParams.isLoadAll()) {
            log.info("load all collection !");
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            for (String collectionName : collectionNames) {
                try {
                    log.info("Loading collection [" + collectionName + "]");
                    long startLoadTime = System.currentTimeMillis();
                    boolean loadState;
                    LoadCollectionReq collectionReq = LoadCollectionReq.builder().collectionName(collectionName)
                            .skipLoadDynamicField(loadParams.isSkipLoadDynamicField())
                            .async(false)
                            .timeout(600000L)
                            .build();
                    if (loadParams.getLoadFields().size()>0){
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
                    loadResultList.add(LoadResult.LoadResultItem.builder()
                            .collectionName(collectionName)
                            .commonResult(CommonResult.builder()
                                    .result(ResultEnum.SUCCESS.result).build())
                            .costTimes((endLoadTime - startLoadTime) / 1000.00)
                            .build());
                } catch (Exception e) {
                    log.error("load [" + collectionName + "] failed! reason:" + e.getMessage());
                    loadResultList.add(LoadResult.LoadResultItem.builder()
                            .collectionName(collectionName)
                            .commonResult(CommonResult.builder()
                                    .result(ResultEnum.EXCEPTION.result)
                                    .message(e.getMessage()).build())
                            .build());
                }
            }
        } else {
            String collectionName = (loadParams.getCollectionName() == null || loadParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(globalCollectionNames.size() - 1) : loadParams.getCollectionName();

            try {
                log.info("Loading collection [" + collectionName + "]");
                long startLoadTime = System.currentTimeMillis();
                boolean loadState = false;
                LoadCollectionReq collectionReq = LoadCollectionReq.builder().collectionName(collectionName)
                        .skipLoadDynamicField(loadParams.isSkipLoadDynamicField())
                        .async(false)
                        .timeout(60000L)
                        .build();
                if (loadParams.getLoadFields().size()>0){
                    collectionReq.setLoadFields(loadParams.getLoadFields());
                }
                milvusClientV2.loadCollection(collectionReq);
                do {
                    loadState = milvusClientV2.getLoadState(GetLoadStateReq.builder()
                            .collectionName(collectionName).build());
                    Thread.sleep(1000L);
                } while (!loadState);
                long endLoadTime = System.currentTimeMillis();
                log.info("Load collection [" + collectionName + "] cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
                loadResultList.add(LoadResult.LoadResultItem.builder()
                        .collectionName(collectionName)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.SUCCESS.result).build())
                        .costTimes((endLoadTime - startLoadTime) / 1000.00)
                        .build());
            } catch (Exception e) {
                log.error("load [" + collectionName + "] failed! reason:" + e.getMessage());
                loadResultList.add(LoadResult.LoadResultItem.builder()
                        .collectionName(collectionName)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message(e.getMessage()).build())
                        .build());
            }
        }
        return LoadResult.builder().loadResultList(loadResultList).build();
    }
}
