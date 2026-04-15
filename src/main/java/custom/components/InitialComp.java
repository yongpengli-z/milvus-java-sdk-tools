package custom.components;

import custom.entity.InitialParams;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.database.request.DropDatabaseReq;
import io.milvus.v2.service.database.response.ListDatabasesResp;
import io.milvus.v2.service.utility.request.DropAliasReq;
import io.milvus.v2.service.utility.request.ListAliasesReq;
import io.milvus.v2.service.utility.response.ListAliasResp;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;


/**
 * @Author yongpeng.li @Date 2024/6/4 17:37
 */
@Slf4j
public class InitialComp {

    /**
     * 初始化环境--包含清除存量collection和非default数据库
     *
     * @param initialParams InitialParams
     */
    public static void initialRunning(InitialParams initialParams) {
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        List<String> collectionNames =
                listCollectionsResp.getCollectionNames();
        log.info("List collection: " + collectionNames);
        globalCollectionNames.addAll(collectionNames);
        if (initialParams.isCleanCollection()) {
            if (collectionNames.size() > 0) {
                cleanCollections(collectionNames);
                log.info("clean collections in default db successfully!");
            }
            // 清理非 default 的数据库
            cleanNonDefaultDatabases();
        }
    }

    /**
     * 清理指定的 collection 列表（先删 alias 再删 collection）
     */
    private static void cleanCollections(List<String> collectionNames) {
        collectionNames.forEach(x -> {
            try {
                // 先清理 alias，否则 Milvus 不允许删除有 alias 关联的 collection
                ListAliasResp listAliasResp = milvusClientV2.listAliases(
                        ListAliasesReq.builder().collectionName(x).build());
                List<String> aliases = listAliasResp.getAlias();
                if (aliases != null && !aliases.isEmpty()) {
                    log.info("Collection [{}] has aliases: {}, dropping them first", x, aliases);
                    for (String alias : aliases) {
                        milvusClientV2.dropAlias(DropAliasReq.builder().alias(alias).build());
                        log.info("Dropped alias [{}] for collection [{}]", alias, x);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to drop aliases for collection [{}]: {}", x, e.getMessage());
            }
            milvusClientV2.dropCollection(DropCollectionReq.builder().collectionName(x).build());
        });
        globalCollectionNames.clear();
    }

    /**
     * 列出所有数据库，对非 default 的 db 逐个清理其 collections 后 drop 该 db，最后切回 default
     */
    private static void cleanNonDefaultDatabases() {
        try {
            ListDatabasesResp listDatabasesResp = milvusClientV2.listDatabases();
            List<String> dbNames = listDatabasesResp.getDatabaseNames();
            log.info("List databases: {}", dbNames);
            for (String dbName : dbNames) {
                if ("default".equalsIgnoreCase(dbName)) {
                    continue;
                }
                log.info("Cleaning database [{}]", dbName);
                try {
                    milvusClientV2.useDatabase(dbName);
                    ListCollectionsResp resp = milvusClientV2.listCollections();
                    List<String> collections = resp.getCollectionNames();
                    if (collections != null && !collections.isEmpty()) {
                        log.info("Database [{}] has collections: {}, dropping them", dbName, collections);
                        cleanCollections(collections);
                    }
                    milvusClientV2.useDatabase("default");
                    milvusClientV2.dropDatabase(DropDatabaseReq.builder().databaseName(dbName).build());
                    log.info("Dropped database [{}]", dbName);
                } catch (Exception e) {
                    log.warn("Failed to clean database [{}]: {}", dbName, e.getMessage());
                    try {
                        milvusClientV2.useDatabase("default");
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list/clean databases: {}", e.getMessage());
        }
    }
}
