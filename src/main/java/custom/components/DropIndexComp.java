package custom.components;

import custom.entity.DropIndexParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DropIndexResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.index.request.DropIndexReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DropIndexComp {
    public static DropIndexResult dropIndex(DropIndexParams dropIndexParams) {
        CommonResult commonResult;
        String collectionName = (dropIndexParams.getCollectionName() == null || dropIndexParams.getCollectionName().equalsIgnoreCase("")) ?
                globalCollectionNames.get(0) : dropIndexParams.getCollectionName();
        try {
            milvusClientV2.dropIndex(DropIndexReq.builder()
                    .collectionName(collectionName)
                    .fieldName(dropIndexParams.getFieldName())
                    .build());
            commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        } catch (Exception e) {
            commonResult = CommonResult.builder().result(ResultEnum.EXCEPTION.result)
                    .message(e.getMessage()).build();
        }
        return DropIndexResult.builder().collectionName(collectionName)
                .fieldName(dropIndexParams.getFieldName())
                .commonResult(commonResult).build();
    }
}
