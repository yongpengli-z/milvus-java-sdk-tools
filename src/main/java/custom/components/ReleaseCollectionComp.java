package custom.components;

import custom.common.CommonFunction;
import custom.entity.ReleaseParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ReleaseResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.ReleaseCollectionReq;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ReleaseCollectionComp {
    public static ReleaseResult releaseCollection(ReleaseParams releaseParams) {
        List<ReleaseResult.ReleaseResultItem> releaseResultList = new ArrayList<>();

        for (String collectionName : resolveTargetCollections(releaseParams)) {
            releaseResultList.add(releaseOne(collectionName));
        }

        // assertions
        List<String> assertMessages = new ArrayList<>();
        for (ReleaseResult.ReleaseResultItem item : releaseResultList) {
            if (item.getCommonResult().getResult().equals(ResultEnum.EXCEPTION.result)) {
                assertMessages.add("[ASSERT FAIL] releaseCollection [" + item.getCollectionName() + "] failed: " + item.getCommonResult().getMessage());
            }
        }
        if (!assertMessages.isEmpty()) {
            log.warn("ReleaseCollection assertions: " + assertMessages);
        }
        return ReleaseResult.builder().releaseResultList(releaseResultList).assertMessages(assertMessages).build();
    }

    /**
     * 解析本次要 release 的 collection 列表。
     * 设置了 collectionNamePrefix 或 collectionRangeStart>=0 时进入多 collection 模式：
     * 目标集合 = releaseAll ? 实例全量列表 : globalCollectionNames 池子，再按前缀+区间过滤。
     */
    private static List<String> resolveTargetCollections(ReleaseParams releaseParams) {
        boolean multiMode = (releaseParams.getCollectionNamePrefix() != null && !releaseParams.getCollectionNamePrefix().equalsIgnoreCase(""))
                || releaseParams.getCollectionRangeStart() >= 0;
        if (multiMode) {
            List<String> source = releaseParams.isReleaseAll()
                    ? milvusClientV2.listCollections().getCollectionNames()
                    : globalCollectionNames;
            List<String> target = CommonFunction.filterCollectionPool(source,
                    releaseParams.getCollectionNamePrefix(),
                    releaseParams.getCollectionRangeStart(),
                    releaseParams.getCollectionRangeEnd());
            log.info("Release 多 collection 模式：共 {} 个 collection 将被 release", target.size());
            return target;
        }
        if (releaseParams.isReleaseAll()) {
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            List<String> collectionNames = listCollectionsResp.getCollectionNames();
            log.info("Release all collections: " + collectionNames);
            return collectionNames;
        }
        String collectionName = (releaseParams.getCollectionName() == null || releaseParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1) : releaseParams.getCollectionName();
        return Collections.singletonList(collectionName);
    }

    private static ReleaseResult.ReleaseResultItem releaseOne(String collectionName) {
        log.info("Release collection [" + collectionName + "]");
        try {
            milvusClientV2.releaseCollection(ReleaseCollectionReq.builder()
                    .collectionName(collectionName).build());
            return ReleaseResult.ReleaseResultItem.builder()
                    .collectionName(collectionName)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build()).build();
        } catch (Exception e) {
            return ReleaseResult.ReleaseResultItem.builder()
                    .collectionName(collectionName)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage()).build())
                    .build();
        }
    }
}
