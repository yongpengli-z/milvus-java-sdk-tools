package custom.components;

import custom.entity.ReleaseParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ReleaseResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ReleaseCollectionComp {
    public static ReleaseResult releaseCollection(ReleaseParams releaseParams) {
        List<ReleaseResult.ReleaseResultItem> releaseResultList = new ArrayList<>();
        if (releaseParams.isReleaseAll()) {
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            log.info("Release all collection: " + collectionNames);
            for (String collectionName : collectionNames) {
                milvusClientV2.releaseCollection(ReleaseCollectionReq.builder()
                        .collectionName(collectionName).build());
                log.info("Release collection [" + collectionName + "]");
                releaseResultList.add(ReleaseResult.ReleaseResultItem.builder()
                        .collectionName(collectionName)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.SUCCESS.result).build()).build());
            }
        } else {
            String collectionName = (releaseParams.getCollectionName() == null || releaseParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(0) : releaseParams.getCollectionName();
            milvusClientV2.releaseCollection(ReleaseCollectionReq.builder()
                    .collectionName(collectionName).build());
            log.info("Release collection [" + collectionName + "]");
            releaseResultList.add(ReleaseResult.ReleaseResultItem.builder()
                    .collectionName(collectionName)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build()).build());
        }
        try {
            log.info("sleep 30s...");
            Thread.sleep(1000 * 30);
        } catch (InterruptedException e) {
            log.error("release collection:" + e.getMessage());
        }
        return ReleaseResult.builder().releaseResultList(releaseResultList).build();
    }
}
