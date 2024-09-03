package custom.components;

import custom.entity.ReleaseParams;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ReleaseCollectionComp {
    public static void releaseCollection(ReleaseParams releaseParams){
        if (releaseParams.isReleaseAll()){

            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            log.info("Release all collection: "+ collectionNames);
            for (String collectionName : collectionNames) {
                milvusClientV2.releaseCollection(ReleaseCollectionReq.builder()
                        .collectionName(collectionName).build());
                log.info("Release collection ["+ collectionName+"]");
            }
        }else {
            String collectionName=(releaseParams.getCollectionName()==null||releaseParams.getCollectionName().equalsIgnoreCase(""))?
                    globalCollectionNames.get(0):releaseParams.getCollectionName();
            milvusClientV2.releaseCollection(ReleaseCollectionReq.builder()
                    .collectionName(collectionName).build());
            log.info("Release collection ["+ collectionName+"]");
        }
    }
}
