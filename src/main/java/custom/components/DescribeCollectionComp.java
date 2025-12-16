package custom.components;

import custom.entity.DescribeCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DescribeCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DescribeCollectionComp {
    public static DescribeCollectionResult describeCollection(DescribeCollectionParams describeCollectionParams) {
        String collection = (describeCollectionParams.getCollectionName() == null ||
                describeCollectionParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1) : describeCollectionParams.getCollectionName();
        DescribeCollectionReq describeCollectionReq = DescribeCollectionReq.builder()
                .collectionName(collection).build();
        if (describeCollectionParams.getDatabaseName() != null && !describeCollectionParams.getDatabaseName().equalsIgnoreCase("")) {
            describeCollectionReq.setDatabaseName(describeCollectionParams.getDatabaseName());
        }
        DescribeCollectionResult describeCollectionResult = DescribeCollectionResult.builder().build();

        try {
            DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(describeCollectionReq);
            describeCollectionResult.setDescribeCollectionResp(describeCollectionResp);
            describeCollectionResult.setCommonResult(CommonResult.builder()
                    .result(ResultEnum.SUCCESS.result)
                    .build());
        } catch (Exception e) {
            log.warn(String.format("describe error: %s", e.getMessage()));
            describeCollectionResult.setCommonResult(CommonResult.builder().result(ResultEnum.WARNING.result)
                    .message(e.getMessage()).build());
        }
        return describeCollectionResult;
    }
}
