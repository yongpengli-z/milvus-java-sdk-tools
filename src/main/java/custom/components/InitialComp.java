package custom.components;

import custom.entity.InitialParams;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static custom.BaseTest.milvusClientV2;


/**
 * @Author yongpeng.li @Date 2024/6/4 17:37
 */
@Slf4j
public class InitialComp {

    /**
     * 初始化环境--包含清除存量collection
     *
     * @param initialParams InitialParams
     */
    public static void initialRunning(InitialParams initialParams) {
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        List<String> collectionNames =
                listCollectionsResp.getCollectionNames();
        log.info("List collection: " + collectionNames);
        if (collectionNames.size() > 0 && initialParams.isCleanCollection()) {
            collectionNames.forEach(x -> {
                milvusClientV2.dropCollection(DropCollectionReq.builder().collectionName(x).build());
            });
            log.info("clean collections successfully!");
        }
    }
}
