package custom.components;

import custom.entity.DropCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DropCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.utility.request.DropAliasReq;
import io.milvus.v2.service.utility.request.ListAliasesReq;
import io.milvus.v2.service.utility.response.ListAliasResp;
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
                    // 先清理 alias，否则 Milvus 不允许删除有 alias 关联的 collection
                    dropAliasesForCollection(collectionName, dropCollectionParams.getDatabaseName());
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
                // 先清理 alias
                dropAliasesForCollection(collectionName, dropCollectionParams.getDatabaseName());
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
        // assertions
        List<String> assertMessages = new ArrayList<>();
        for (DropCollectionResult.DropCollectionResultItem item : dropCollectionResultList) {
            if (item.getCommonResult().getResult().equals(ResultEnum.EXCEPTION.result)) {
                assertMessages.add("[ASSERT FAIL] dropCollection [" + item.getCollectionName() + "] failed: " + item.getCommonResult().getMessage());
            }
        }
        if (!assertMessages.isEmpty()) {
            log.warn("DropCollection assertions: " + assertMessages);
        }
        return DropCollectionResult.builder().dropCollectionResultList(dropCollectionResultList).assertMessages(assertMessages).build();
    }

    /**
     * 删除 collection 关联的所有 alias
     */
    private static void dropAliasesForCollection(String collectionName, String databaseName) {
        try {
            ListAliasesReq.ListAliasesReqBuilder builder = ListAliasesReq.builder().collectionName(collectionName);
            if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
                builder.databaseName(databaseName);
            }
            ListAliasResp listAliasResp = milvusClientV2.listAliases(builder.build());
            List<String> aliases = listAliasResp.getAlias();
            if (aliases != null && !aliases.isEmpty()) {
                log.info("Collection [{}] has aliases: {}, dropping them first", collectionName, aliases);
                for (String alias : aliases) {
                    DropAliasReq dropAliasReq = DropAliasReq.builder().alias(alias).build();
                    if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
                        dropAliasReq.setDatabaseName(databaseName);
                    }
                    milvusClientV2.dropAlias(dropAliasReq);
                    log.info("Dropped alias [{}] for collection [{}]", alias, collectionName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to drop aliases for collection [{}]: {}", collectionName, e.getMessage());
        }
    }
}
