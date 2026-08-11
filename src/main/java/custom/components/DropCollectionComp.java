package custom.components;

import custom.entity.DropCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DropCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.ListCollectionsReq;
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
        if (dropCollectionParams.isCollectionNameUsePrefix()
                && dropCollectionParams.getCollectionName() != null
                && !dropCollectionParams.getCollectionName().equalsIgnoreCase("")) {
            List<String> collectionNames = collectionNamesByPrefix(dropCollectionParams.getCollectionName(), dropCollectionParams.getDatabaseName());
            log.info("Drop collections by prefix [{}], dropAll [{}]: {}", dropCollectionParams.getCollectionName(), dropCollectionParams.isDropAll(), collectionNames);
            if (collectionNames.isEmpty()) {
                dropCollectionResultList.add(DropCollectionResult.DropCollectionResultItem.builder()
                        .collectionName(dropCollectionParams.getCollectionName())
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.FAIL.result)
                                .message("no collection matched prefix: " + dropCollectionParams.getCollectionName())
                                .build())
                        .build());
            } else if (dropCollectionParams.isDropAll()) {
                for (String collectionName : collectionNames) {
                    dropOneCollection(collectionName, dropCollectionParams.getDatabaseName(), dropCollectionResultList);
                }
            } else {
                String collectionName = collectionNames.get(collectionNames.size() - 1);
                dropOneCollection(collectionName, dropCollectionParams.getDatabaseName(), dropCollectionResultList);
            }
        } else if (dropCollectionParams.isDropAll()) {
            List<String> collectionNames = listCollectionNames(dropCollectionParams.getDatabaseName());
            log.info("Drop all collections: " + collectionNames);
            for (String collectionName : collectionNames) {
                dropOneCollection(collectionName, dropCollectionParams.getDatabaseName(), dropCollectionResultList);
            }
        } else {
            String collectionName = (dropCollectionParams.getCollectionName() == null || dropCollectionParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(globalCollectionNames.size() - 1) : dropCollectionParams.getCollectionName();
            dropOneCollection(collectionName, dropCollectionParams.getDatabaseName(), dropCollectionResultList);
        }
        // assertions
        List<String> assertMessages = new ArrayList<>();
        for (DropCollectionResult.DropCollectionResultItem item : dropCollectionResultList) {
            if (item.getCommonResult().getResult().equals(ResultEnum.FAIL.result)) {
                assertMessages.add("[ASSERT FAIL] dropCollection [" + item.getCollectionName() + "] failed: " + item.getCommonResult().getMessage());
            }
        }
        if (!assertMessages.isEmpty()) {
            log.warn("DropCollection assertions: " + assertMessages);
        }
        return DropCollectionResult.builder().dropCollectionResultList(dropCollectionResultList).assertMessages(assertMessages).build();
    }

    private static List<String> collectionNamesByPrefix(String prefix, String databaseName) {
        List<String> collectionNames = listCollectionNames(databaseName);
        List<String> matched = new ArrayList<>();
        for (String collectionName : collectionNames) {
            if (collectionName != null && collectionName.startsWith(prefix)) {
                matched.add(collectionName);
            }
        }
        return matched;
    }

    private static List<String> listCollectionNames(String databaseName) {
        ListCollectionsResp listCollectionsResp;
        if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
            listCollectionsResp = milvusClientV2.listCollectionsV2(ListCollectionsReq.builder()
                    .databaseName(databaseName)
                    .build());
        } else {
            listCollectionsResp = milvusClientV2.listCollections();
        }
        return listCollectionsResp.getCollectionNames();
    }

    private static void dropOneCollection(String collectionName, String databaseName,
                                          List<DropCollectionResult.DropCollectionResultItem> dropCollectionResultList) {
        try {
            log.info("Drop collection: " + collectionName);
            dropAliasesForCollection(collectionName, databaseName);
            DropCollectionReq dropCollectionReq = DropCollectionReq.builder()
                    .collectionName(collectionName).build();
            if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
                dropCollectionReq.setDatabaseName(databaseName);
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
                            .result(ResultEnum.FAIL.result)
                            .message(e.getMessage())
                            .build())
                    .build());
        }
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
