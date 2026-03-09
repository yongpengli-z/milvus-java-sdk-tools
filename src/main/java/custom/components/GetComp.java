package custom.components;

import custom.entity.GetParams;
import custom.entity.result.CommonResult;
import custom.entity.result.GetResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.vector.request.GetReq;
import io.milvus.v2.service.vector.response.GetResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

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
            // assertions
            List<String> assertMessages = new ArrayList<>();
            int resultSize = getResp.getGetResults().size();
            int requestSize = getParams.getIds() != null ? getParams.getIds().size() : 0;
            if (resultSize == 0) {
                assertMessages.add("[ASSERT WARN] get returned 0 results");
            }
            if (requestSize > 0 && resultSize != requestSize) {
                assertMessages.add(String.format("[ASSERT WARN] get returned %d results, expected %d (ids count)", resultSize, requestSize));
            }
            if (!assertMessages.isEmpty()) {
                log.warn("Get assertions: " + assertMessages);
            }
            return GetResult.builder()
                    .getResults(getResp.getGetResults())
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .assertMessages(assertMessages)
                    .build();
        } catch (Exception e) {
            log.error("Get entities from collection [{}] failed: {}", collectionName, e.getMessage());
            List<String> assertMessages = new ArrayList<>();
            assertMessages.add("[ASSERT FAIL] get exception: " + e.getMessage());
            log.warn("Get assertions: " + assertMessages);
            return GetResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .assertMessages(assertMessages)
                    .build();
        }
    }
}
