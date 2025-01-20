package custom.components;

import custom.entity.CompactParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CompactResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.common.CompactionState;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.utility.request.CompactReq;
import io.milvus.v2.service.utility.request.GetCompactionStateReq;
import io.milvus.v2.service.utility.response.CompactResp;
import io.milvus.v2.service.utility.response.GetCompactionStateResp;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CompactComp {
    public static CompactResult compact(CompactParams compactParams) {
        List<CompactResult.CompactResultItem> compactResultItemList = new ArrayList<>();
        List<String> collectionNames = new ArrayList<>();
        if (compactParams.isCompactAll()) {
            log.info("compact all collection !");
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            collectionNames = listCollectionsResp.getCollectionNames();
        } else {
            String collectionName = (compactParams.getCollectionName() == null || compactParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(globalCollectionNames.size() - 1) : compactParams.getCollectionName();
            collectionNames.add(collectionName);
        }
        for (String collectionName : collectionNames) {
            log.info("compact collection [" + collectionName + "]");
            long startCompactTime = System.currentTimeMillis();
            LocalDateTime startTime = LocalDateTime.now();
            CompactResp compactResult = milvusClientV2.compact(CompactReq.builder()
                    .collectionName(collectionName)
                    .isClustering(compactParams.isClustering())
                    .build());
            boolean compactState = false;
            do {
                GetCompactionStateResp compactionState = milvusClientV2.getCompactionState(GetCompactionStateReq.builder()
                        .compactionID(compactResult.getCompactionID())
                        .build());
                CompactionState state = compactionState.getState();
                if (state.getCode() == CompactionState.Completed.getCode()) {
                    compactState = true;
                    long endLoadTime = System.currentTimeMillis();
                    compactResultItemList.add(CompactResult.CompactResultItem.builder()
                            .collectionName(collectionName)
                            .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                            .costTimes((endLoadTime - startCompactTime) / 1000.00)
                            .build());
                }
                if (LocalDateTime.now().isAfter(startTime.plusMinutes(30L))) {
                    compactState = true;
                    compactResultItemList.add(CompactResult.CompactResultItem.builder()
                            .collectionName(collectionName)
                            .commonResult(CommonResult.builder().result(ResultEnum.EXCEPTION.result).message("compact 超时！").build())
                            .build());
                }
            } while (!compactState);
        }

        CompactResult build = CompactResult.builder().
                compactResultList(compactResultItemList).
                build();
        log.info("Compact result:" + build);
        return build;

    }
}
