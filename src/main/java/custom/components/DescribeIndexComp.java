package custom.components;

import custom.entity.DescribeIndexParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DescribeIndexResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.request.ListIndexesReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DescribeIndexComp {
    public static DescribeIndexResult describeIndex(DescribeIndexParams describeIndexParams) {
        String collectionName = (describeIndexParams.getCollectionName() == null ||
                describeIndexParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : describeIndexParams.getCollectionName();

        boolean fieldNameEmpty = describeIndexParams.getFieldName() == null
                || describeIndexParams.getFieldName().equalsIgnoreCase("");
        boolean indexNameEmpty = describeIndexParams.getIndexName() == null
                || describeIndexParams.getIndexName().equalsIgnoreCase("");

        try {
            // fieldName 和 indexName 都未传：列出所有索引并逐个 describe
            if (fieldNameEmpty && indexNameEmpty) {
                log.info("Describe all indexes for collection [{}]", collectionName);
                List<String> indexNames = milvusClientV2.listIndexes(ListIndexesReq.builder()
                        .collectionName(collectionName)
                        .build());
                log.info("Collection [{}] has indexes: {}", collectionName, indexNames);

                List<DescribeIndexResp> allDescriptions = new ArrayList<>();
                for (String idxName : indexNames) {
                    DescribeIndexResp resp = milvusClientV2.describeIndex(DescribeIndexReq.builder()
                            .collectionName(collectionName)
                            .indexName(idxName)
                            .build());
                    allDescriptions.add(resp);
                }
                log.info("Describe all indexes success, count: {}", allDescriptions.size());
                return DescribeIndexResult.builder()
                        .allIndexDescriptions(allDescriptions)
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.SUCCESS.result)
                                .build())
                        .build();
            }

            // 原有逻辑：按 fieldName / indexName 查询单个索引
            log.info("Describe index for collection [{}], field [{}]", collectionName, describeIndexParams.getFieldName());
            DescribeIndexReq.DescribeIndexReqBuilder builder = DescribeIndexReq.builder()
                    .collectionName(collectionName)
                    .fieldName(describeIndexParams.getFieldName());
            if (!indexNameEmpty) {
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
