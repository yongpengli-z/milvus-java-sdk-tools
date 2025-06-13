package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateCollectionResult;
import custom.entity.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;


import static custom.BaseTest.globalCollectionNames;

@Slf4j
public class CreateCollectionComp {
    public static CreateCollectionResult createCollection(CreateCollectionParams createCollectionParams) {
        String collection = null;
        CommonResult commonResult;
        try {
            collection = CommonFunction.genCommonCollection(createCollectionParams.getCollectionName(),
                    createCollectionParams.isEnableDynamic(), createCollectionParams.getShardNum(), createCollectionParams.getNumPartitions(),
                    createCollectionParams.getFieldParamsList(),createCollectionParams.getFunctionParams());
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
        return CreateCollectionResult.builder()
                .commonResult(commonResult)
                .collectionName(collection).build();
    }

}
