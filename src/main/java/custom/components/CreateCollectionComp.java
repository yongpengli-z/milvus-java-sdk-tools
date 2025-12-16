package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import lombok.extern.slf4j.Slf4j;


import java.util.Map;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CreateCollectionComp {
    public static CreateCollectionResult createCollection(CreateCollectionParams createCollectionParams) {
        String collection = null;
        CommonResult commonResult;
        try {
            collection = CommonFunction.genCommonCollection(createCollectionParams.getCollectionName(),
                    createCollectionParams.isEnableDynamic(), createCollectionParams.getShardNum(), createCollectionParams.getNumPartitions(),
                    createCollectionParams.getFieldParamsList(), createCollectionParams.getFunctionParams(), createCollectionParams.getProperties()
                    , createCollectionParams.getDatabaseName());
            log.info("create collection [" + collection + "] success!");
            commonResult = CommonResult.builder()
                    .result(ResultEnum.SUCCESS.result)
                    .build();
        } catch (Exception e) {
            commonResult = CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message(e.getMessage())
                    .build();
        }
        globalCollectionNames.add(collection);
        // 检查properties
        if (createCollectionParams.getProperties() != null && createCollectionParams.getProperties().size() > 0) {
            DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder()
                    .collectionName(collection).build());
            Map<String, String> properties =
                    describeCollectionResp.getProperties();
            for (String s : properties.keySet()) {
                log.info(String.format("property %s : %s", s, properties.get(s)));
            }
        }
        return CreateCollectionResult.builder()
                .commonResult(commonResult)
                .collectionName(collection).build();
    }

}
