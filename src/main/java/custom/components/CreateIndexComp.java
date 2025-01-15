package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateIndexParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateIndexResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CreateIndexComp {
    public static CreateIndexResult CreateIndex(CreateIndexParams createIndexParams) {
        String collectionName = (createIndexParams.getCollectionName() == null || createIndexParams.getCollectionName().equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : createIndexParams.getCollectionName();
        CommonResult commonResult;
        try {
            CommonFunction.createCommonIndex(collectionName, createIndexParams.getIndexParams());
            commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        } catch (Exception e) {
            log.error(e.getMessage());
            commonResult = CommonResult.builder().result(ResultEnum.EXCEPTION.result)
                    .message(e.getMessage()).build();
        }
        // describe index
        DescribeIndexResp describeIndexResp = milvusClientV2.describeIndex(DescribeIndexReq.builder().collectionName(collectionName).build());
        List<DescribeIndexResp.IndexDesc> indexDescriptions =
                describeIndexResp.getIndexDescriptions();
        for (DescribeIndexResp.IndexDesc indexDescription : indexDescriptions) {
            log.info(indexDescription.getFieldName() + "-" + indexDescription.getIndexType() + "-" + indexDescription.getIndexState());
        }

        return CreateIndexResult.builder()
                .collectionName(collectionName)
                .indexParams(createIndexParams.getIndexParams())
                .commonResult(commonResult)
                .build();
    }
}
