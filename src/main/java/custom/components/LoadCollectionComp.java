package custom.components;

import custom.entity.LoadParams;
import io.milvus.grpc.LoadState;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class LoadCollectionComp {
    public static void loadCollection(LoadParams loadParams) {
        if (loadParams.isLoadAll()) {
            log.info("load all collection !");
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            for (String collectionName : collectionNames) {
                try {
                    log.info("Loading collection ["+collectionName+"]");
                    long startLoadTime = System.currentTimeMillis();
                    boolean loadState=false;
                    milvusClientV2.loadCollection(LoadCollectionReq.builder().collectionName(collectionName)
                            .async(true)
                            .build());
                    do {
                        loadState = milvusClientV2.getLoadState(GetLoadStateReq.builder()
                                .collectionName(collectionName).build());
                        Thread.sleep(1000L);
                    } while (!loadState);
                    long endLoadTime = System.currentTimeMillis();
                    log.info("Load collection ["+collectionName+"] cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
                } catch (Exception e) {
                    log.error("load ["+collectionName+"] failed! reason:"+e.getMessage());
                }
            }
        }else{
            String collectionName=(loadParams.getCollectionName()==null||loadParams.getCollectionName().equalsIgnoreCase(""))?
                    globalCollectionNames.get(0):loadParams.getCollectionName();
            try {
                log.info("Loading collection ["+collectionName+"]");
                long startLoadTime = System.currentTimeMillis();
                boolean loadState=false;
                milvusClientV2.loadCollection(LoadCollectionReq.builder().collectionName(collectionName)
                        .async(true)
                        .build());
                do {
                    loadState = milvusClientV2.getLoadState(GetLoadStateReq.builder()
                            .collectionName(collectionName).build());
                    Thread.sleep(1000L);
                } while (!loadState);
                long endLoadTime = System.currentTimeMillis();
                log.info("Load collection ["+collectionName+"] cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
            } catch (Exception e) {
                log.error("load ["+collectionName+"] failed! reason:"+e.getMessage());
            }
        }
    }
}
