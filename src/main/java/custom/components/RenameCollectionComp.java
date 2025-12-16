package custom.components;

import custom.entity.RenameCollectionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.RenameCollectionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.request.RenameCollectionReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class RenameCollectionComp {
    public static RenameCollectionResult renameCollection(RenameCollectionParams renameCollectionParams) {
        String collection = (renameCollectionParams.getCollectionName() == null ||
                renameCollectionParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1) : renameCollectionParams.getCollectionName();
        RenameCollectionReq renameCollectionReq = RenameCollectionReq.builder()
                .collectionName(collection)
                .newCollectionName(renameCollectionParams.getNewCollectionName())
                .build();
        if (renameCollectionParams.getDatabaseName() != null && !renameCollectionParams.getDatabaseName().equalsIgnoreCase("")) {
            renameCollectionReq.setDatabaseName(renameCollectionParams.getDatabaseName());
        }
        RenameCollectionResult collectionResult = RenameCollectionResult.builder().build();
        try {
            milvusClientV2.renameCollection(renameCollectionReq);
            collectionResult.setCommonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build());
        } catch (Exception e) {
            collectionResult.setCommonResult(CommonResult.builder().result(ResultEnum.WARNING.result)
                    .message(e.getMessage()).build());
        }
        return collectionResult;

    }
}
