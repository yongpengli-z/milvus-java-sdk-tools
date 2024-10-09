package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.param.Constant;
import io.milvus.v2.service.collection.request.AlterCollectionReq;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
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
                    createCollectionParams.getFieldParamsList());
            log.info("create collection [" + collection + "] success!");
            commonResult= CommonResult.builder()
                    .result(ResultEnum.SUCCESS.result)
                    .build();
        } catch (Exception e) {
            commonResult= CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message(e.getMessage())
                    .build();
        }
        globalCollectionNames.add(collection);

        Map<String, String> map = new HashMap<>() {{
            put(Constant.MMAP_ENABLED, String.valueOf(createCollectionParams.isEnableMmap()));
        }};
        milvusClientV2.alterCollection(AlterCollectionReq.builder()
                .properties(map)
                .collectionName(collection)
                .build());
        log.info("alter collection [" + collection + "] scalar mmap: " + createCollectionParams.isEnableMmap());
        return CreateCollectionResult.builder()
                .commonResult(commonResult)
                .collectionName(collection).build();
    }

}
