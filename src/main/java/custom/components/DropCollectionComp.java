package custom.components;

import custom.entity.DropCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DropCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DropCollectionComp {
    public static DropCollectionResult dropCollection(DropCollectionParams dropCollectionParams) {
        List<DropCollectionResult.DropCollectionResultItem> dropCollectionResultList = new ArrayList<>();
        if (dropCollectionParams.isDropAll()) {
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            log.info("Drop all collections: " + collectionNames);
            for (String collectionName : collectionNames) {
                try {
                    DropCollectionReq dropCollectionReq = DropCollectionReq.builder()
                            .collectionName(collectionName).build();
                    if (dropCollectionParams.getDatabaseName() != null && !dropCollectionParams.getDatabaseName().equalsIgnoreCase("")) {
                        dropCollectionReq.setDatabaseName(dropCollectionParams.getDatabaseName());
                    }
                    milvusClientV2.dropCollection(dropCollectionReq);
                    // 清空globalCollectionNames
                    globalCollectionNames.clear();
                    dropCollectionResultList.add(DropCollectionResult.DropCollectionResultItem.builder()
                            .collectionName(collectionName)
                            .commonResult(CommonResult.builder()
                                    .result(ResultEnum.SUCCESS.result)
                                    .build())
                            .build());
                } catch (Exception e) {
                    dropCollectionResultList.add(DropCollectionResult.DropCollectionResultItem.builder()
                            .collectionName(collectionName)
                            .commonResult(CommonResult.builder()
                                    .result(ResultEnum.EXCEPTION.result)
                                    .message(e.getMessage())
                                    .build())
                            .build());
                }
            }
        } else {
            String collectionName = (dropCollectionParams.getCollectionName() == null || dropCollectionParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(globalCollectionNames.size() - 1) : dropCollectionParams.getCollectionName();
            try {
                log.info("Drop collection: " + dropCollectionParams.getCollectionName());
                DropCollectionReq dropCollectionReq = DropCollectionReq.builder()
                        .collectionName(collectionName).build();
                if (dropCollectionParams.getDatabaseName() != null && !dropCollectionParams.getDatabaseName().equalsIgnoreCase("")) {
                    dropCollectionReq.setDatabaseName(dropCollectionParams.getDatabaseName());
                }
                milvusClientV2.dropCollection(dropCollectionReq);
                globalCollectionNames.remove(collectionName);
                dropCollectionResultList.add(DropCollectionResult.DropCollectionResultItem.builder()
                        .collectionName(collectionName)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.SUCCESS.result)
                                .build())
                        .build());
            } catch (Exception e) {
                dropCollectionResultList.add(DropCollectionResult.DropCollectionResultItem.builder()
                        .collectionName(collectionName)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message(e.getMessage())
                                .build())
                        .build());
            }
        }
        return DropCollectionResult.builder().dropCollectionResultList(dropCollectionResultList).build();
    }
}
