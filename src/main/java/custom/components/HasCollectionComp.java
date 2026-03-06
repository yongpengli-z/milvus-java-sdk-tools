package custom.components;

import custom.entity.HasCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.HasCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class HasCollectionComp {
    public static HasCollectionResult hasCollection(HasCollectionParams hasCollectionParams) {
        String collectionName = (hasCollectionParams.getCollectionName() == null ||
                hasCollectionParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : hasCollectionParams.getCollectionName();
        try {
            log.info("Check collection [{}] exists", collectionName);
            Boolean has = milvusClientV2.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            log.info("Has collection [{}]: {}", collectionName, has);
            return HasCollectionResult.builder()
                    .hasCollection(has)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Has collection [{}] failed: {}", collectionName, e.getMessage());
            return HasCollectionResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
