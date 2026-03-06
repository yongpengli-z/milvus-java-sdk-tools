package custom.components;

import custom.entity.DescribeAliasParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DescribeAliasResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.utility.request.DescribeAliasReq;
import io.milvus.v2.service.utility.response.DescribeAliasResp;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DescribeAliasComp {
    public static DescribeAliasResult describeAlias(DescribeAliasParams describeAliasParams) {
        try {
            log.info("Describe alias [{}]", describeAliasParams.getAlias());
            DescribeAliasReq.DescribeAliasReqBuilder builder = DescribeAliasReq.builder()
                    .alias(describeAliasParams.getAlias());
            if (describeAliasParams.getDatabaseName() != null && !describeAliasParams.getDatabaseName().equalsIgnoreCase("")) {
                builder.databaseName(describeAliasParams.getDatabaseName());
            }
            DescribeAliasResp describeAliasResp = milvusClientV2.describeAlias(builder.build());
            log.info("Alias [{}] -> collection [{}], database [{}]",
                    describeAliasResp.getAlias(), describeAliasResp.getCollectionName(), describeAliasResp.getDatabaseName());
            return DescribeAliasResult.builder()
                    .alias(describeAliasResp.getAlias())
                    .collectionName(describeAliasResp.getCollectionName())
                    .databaseName(describeAliasResp.getDatabaseName())
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Describe alias [{}] failed: {}", describeAliasParams.getAlias(), e.getMessage());
            return DescribeAliasResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
