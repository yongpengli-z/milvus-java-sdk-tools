package custom.components;

import custom.entity.ListCollectionsParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ListCollectionsResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ListCollectionsComp {
    public static ListCollectionsResult listCollections(ListCollectionsParams listCollectionsParams) {
        try {
            // 如果指定了 databaseName，先切换到该 database
            String databaseName = listCollectionsParams.getDatabaseName();
            if (databaseName != null && !databaseName.isEmpty()) {
                log.info("切换到 database: {}", databaseName);
                milvusClientV2.useDatabase(databaseName);
            }
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            log.info("List collections({}个): {}", listCollectionsResp.getCollectionNames().size(), listCollectionsResp.getCollectionNames());
            return ListCollectionsResult.builder()
                    .collectionNames(listCollectionsResp.getCollectionNames())
                    .collectionCount(listCollectionsResp.getCollectionNames().size())
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("List collections failed: {}", e.getMessage());
            return ListCollectionsResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
