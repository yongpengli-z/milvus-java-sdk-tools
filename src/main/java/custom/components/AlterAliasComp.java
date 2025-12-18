package custom.components;

import custom.entity.AlterAliasParams;
import custom.entity.result.AlterAliasResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.utility.request.AlterAliasReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class AlterAliasComp {
    public static AlterAliasResult alterAlias(AlterAliasParams alterAliasParams) {
        // 先search collection
        String collection = (alterAliasParams.getCollectionName() == null ||
                alterAliasParams.getCollectionName().equalsIgnoreCase("")) ?
                globalCollectionNames.get(globalCollectionNames.size() - 1) : alterAliasParams.getCollectionName();
        AlterAliasReq alterAliasReq = AlterAliasReq.builder().collectionName(collection)
                .alias(alterAliasParams.getAlias()).build();
        if (alterAliasParams.getDatabaseName() != null && !alterAliasParams.getDatabaseName().equalsIgnoreCase("")) {
            alterAliasReq.setDatabaseName(alterAliasParams.getDatabaseName());
        }
        AlterAliasResult alterAliasResult = AlterAliasResult.builder().build();
        try {
            milvusClientV2.alterAlias(alterAliasReq);
            alterAliasResult.setCommonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build());
        } catch (Exception e) {
            alterAliasResult.setCommonResult(CommonResult.builder().result(ResultEnum.WARNING.result)
                    .message(e.getMessage())
                    .build());
        }
        return alterAliasResult;


    }

}
