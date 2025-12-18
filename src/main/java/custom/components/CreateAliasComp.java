package custom.components;

import custom.entity.CreateAliasParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateAliasResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.utility.request.CreateAliasReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CreateAliasComp {
    public static CreateAliasResult createAlias(CreateAliasParams createAliasParams) {
        // 先search collection
        String collection = (createAliasParams.getCollectionName() == null ||
                createAliasParams.getCollectionName().equalsIgnoreCase("")) ?
                globalCollectionNames.get(globalCollectionNames.size() - 1) : createAliasParams.getCollectionName();
        CreateAliasResult createAliasResult = CreateAliasResult.builder().build();
        CreateAliasReq createAliasReq = CreateAliasReq.builder().collectionName(collection).alias(createAliasParams.getAlias()).build();
        if (createAliasParams.getDatabaseName() != null && !createAliasParams.getDatabaseName().equalsIgnoreCase("")) {
            createAliasReq.setDatabaseName(createAliasParams.getDatabaseName());
        }
        try {
            milvusClientV2.createAlias(createAliasReq);
            createAliasResult.setCommonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result)
                    .message(String.format("Collection[%s] create alias[%s] success!", collection, createAliasParams.getAlias())).build());
        } catch (Exception e) {
            log.error("create alias:" + e.getMessage());
            createAliasResult.setCommonResult(CommonResult.builder().result(ResultEnum.WARNING.result).message(e.getMessage()).build());
        }
        return createAliasResult;
    }
}
