package custom.components;

import custom.entity.DescribeIndexParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DescribeIndexResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DescribeIndexComp {
    public static DescribeIndexResult describeIndex(DescribeIndexParams describeIndexParams) {
        String collectionName = (describeIndexParams.getCollectionName() == null ||
                describeIndexParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : describeIndexParams.getCollectionName();
        try {
            log.info("Describe index for collection [{}], field [{}]", collectionName, describeIndexParams.getFieldName());
            DescribeIndexReq.DescribeIndexReqBuilder builder = DescribeIndexReq.builder()
                    .collectionName(collectionName)
                    .fieldName(describeIndexParams.getFieldName());
            if (describeIndexParams.getIndexName() != null && !describeIndexParams.getIndexName().equalsIgnoreCase("")) {
                builder.indexName(describeIndexParams.getIndexName());
            }
            DescribeIndexResp describeIndexResp = milvusClientV2.describeIndex(builder.build());
            log.info("Describe index success: {}", describeIndexResp);
            return DescribeIndexResult.builder()
                    .describeIndexResp(describeIndexResp)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Describe index for collection [{}] failed: {}", collectionName, e.getMessage());
            return DescribeIndexResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
