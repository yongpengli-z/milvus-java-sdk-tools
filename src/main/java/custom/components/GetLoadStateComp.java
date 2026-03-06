package custom.components;

import custom.entity.GetLoadStateParams;
import custom.entity.result.CommonResult;
import custom.entity.result.GetLoadStateResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class GetLoadStateComp {
    public static GetLoadStateResult getLoadState(GetLoadStateParams getLoadStateParams) {
        String collectionName = (getLoadStateParams.getCollectionName() == null ||
                getLoadStateParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : getLoadStateParams.getCollectionName();
        try {
            log.info("Get load state for collection [{}]", collectionName);
            GetLoadStateReq.GetLoadStateReqBuilder builder = GetLoadStateReq.builder()
                    .collectionName(collectionName);
            Boolean loaded = milvusClientV2.getLoadState(builder.build());
            log.info("Collection [{}] load state: {}", collectionName, loaded);
            return GetLoadStateResult.builder()
                    .loaded(loaded)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Get load state for collection [{}] failed: {}", collectionName, e.getMessage());
            return GetLoadStateResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
