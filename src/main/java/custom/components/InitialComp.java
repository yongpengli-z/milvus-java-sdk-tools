package custom.components;

import custom.entity.InitialParams;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
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
     * 初始化环境--包含清除存量collection
     *
     * @param initialParams InitialParams
     */
    public static void initialRunning(InitialParams initialParams) {
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        List<String> collectionNames =
                listCollectionsResp.getCollectionNames();
        log.info("List collection: " + collectionNames);
        globalCollectionNames.addAll(collectionNames);
        if (collectionNames.size() > 0 && initialParams.isCleanCollection()) {
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
                globalCollectionNames.clear();
            });
            log.info("clean collections successfully!");
        }
    }
}
