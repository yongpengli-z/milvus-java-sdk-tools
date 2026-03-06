package custom.components;

import custom.entity.GetParams;
import custom.entity.result.CommonResult;
import custom.entity.result.GetResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.response.GetResp;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class GetComp {
    public static GetResult get(GetParams getParams) {
        String collectionName = (getParams.getCollectionName() == null ||
                getParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : getParams.getCollectionName();
        try {
            log.info("Get entities from collection [{}], ids: {}", collectionName, getParams.getIds());
            GetReq.GetReqBuilder builder = GetReq.builder()
                    .collectionName(collectionName)
                    .ids(getParams.getIds());
            if (getParams.getOutputFields() != null && !getParams.getOutputFields().isEmpty()) {
                builder.outputFields(getParams.getOutputFields());
            }
            GetResp getResp = milvusClientV2.get(builder.build());
            log.info("Get entities success, result size: {}", getResp.getGetResults().size());
            return GetResult.builder()
                    .getResults(getResp.getGetResults())
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Get entities from collection [{}] failed: {}", collectionName, e.getMessage());
            return GetResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
