package custom.components;

import custom.common.CommonFunction;
import custom.entity.ListCollectionsParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ListCollectionsResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

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
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            String prefix = listCollectionsParams.getCollectionNamePrefix();
            if (prefix != null && !prefix.isEmpty()) {
                collectionNames = collectionNames.stream()
                        .filter(name -> name.startsWith(prefix))
                        .collect(Collectors.toList());
                log.info("按前缀 \"{}\" 过滤后命中 {} 个 collection", prefix, collectionNames.size());
            }
            log.info("List collections: {}", CommonFunction.summarizeForLog(collectionNames));
            return ListCollectionsResult.builder()
                    .collectionNames(collectionNames)
                    .collectionCount(collectionNames.size())
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
