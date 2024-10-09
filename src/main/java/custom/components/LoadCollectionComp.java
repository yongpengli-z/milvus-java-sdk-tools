package custom.components;

import custom.entity.LoadParams;
import custom.entity.result.CommonResult;
import custom.entity.result.LoadResult;
import custom.entity.result.ResultEnum;
import io.milvus.grpc.LoadState;
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
        List<CommonResult> commonResultList = new ArrayList<>();
        List<String> collectionNameList = new ArrayList<>();
        List<Double> costTimes = new ArrayList<>();
        if (loadParams.isLoadAll()) {
            log.info("load all collection !");
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            for (String collectionName : collectionNames) {
                collectionNameList.add(collectionName);
                try {
                    log.info("Loading collection [" + collectionName + "]");
                    long startLoadTime = System.currentTimeMillis();
                    boolean loadState;
                    milvusClientV2.loadCollection(LoadCollectionReq.builder().collectionName(collectionName)
                            .async(false).timeout(60000L)
                            .build());
                    do {
                        loadState = milvusClientV2.getLoadState(GetLoadStateReq.builder()
                                .collectionName(collectionName).build());
                        log.info("轮询load结果：" + loadState);
                        Thread.sleep(1000L);
                    } while (!loadState);
                    long endLoadTime = System.currentTimeMillis();
                    log.info("Load collection [" + collectionName + "] cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
                    costTimes.add((endLoadTime - startLoadTime) / 1000.00);
                    commonResultList.add(CommonResult.builder().result(ResultEnum.SUCCESS.result).build());
                } catch (Exception e) {
                    log.error("load [" + collectionName + "] failed! reason:" + e.getMessage());
                    commonResultList.add(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build());
                }
            }
        } else {
            String collectionName = (loadParams.getCollectionName() == null || loadParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(0) : loadParams.getCollectionName();
            collectionNameList.add(collectionName);
            try {
                log.info("Loading collection [" + collectionName + "]");
                long startLoadTime = System.currentTimeMillis();
                boolean loadState = false;
                milvusClientV2.loadCollection(LoadCollectionReq.builder().collectionName(collectionName)
                        .async(true)
                        .build());
                do {
                    loadState = milvusClientV2.getLoadState(GetLoadStateReq.builder()
                            .collectionName(collectionName).build());
                    Thread.sleep(1000L);
                } while (!loadState);
                long endLoadTime = System.currentTimeMillis();
                log.info("Load collection [" + collectionName + "] cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
                costTimes.add((endLoadTime - startLoadTime) / 1000.00);
                commonResultList.add(CommonResult.builder().result(ResultEnum.SUCCESS.result).build());
            } catch (Exception e) {
                log.error("load [" + collectionName + "] failed! reason:" + e.getMessage());
                commonResultList.add(CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message(e.getMessage())
                        .build());
            }
        }
        return LoadResult.builder().commonResults(commonResultList)
                .collectionNames(collectionNameList)
                .costTimes(costTimes).build();
    }
}
