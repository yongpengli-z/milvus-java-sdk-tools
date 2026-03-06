package custom.components;

import custom.entity.ListAliasesParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ListAliasesResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.utility.request.ListAliasesReq;
import io.milvus.v2.service.utility.response.ListAliasResp;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ListAliasesComp {
    public static ListAliasesResult listAliases(ListAliasesParams listAliasesParams) {
        String collectionName = (listAliasesParams.getCollectionName() == null ||
                listAliasesParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : listAliasesParams.getCollectionName();
        try {
            log.info("List aliases for collection [{}]", collectionName);
            ListAliasesReq.ListAliasesReqBuilder builder = ListAliasesReq.builder()
                    .collectionName(collectionName);
            if (listAliasesParams.getDatabaseName() != null && !listAliasesParams.getDatabaseName().equalsIgnoreCase("")) {
                builder.databaseName(listAliasesParams.getDatabaseName());
            }
            ListAliasResp listAliasResp = milvusClientV2.listAliases(builder.build());
            log.info("Collection [{}] aliases: {}", collectionName, listAliasResp.getAlias());
            return ListAliasesResult.builder()
                    .collectionName(listAliasResp.getCollectionName())
                    .aliases(listAliasResp.getAlias())
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("List aliases for collection [{}] failed: {}", collectionName, e.getMessage());
            return ListAliasesResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
