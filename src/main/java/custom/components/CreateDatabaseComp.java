package custom.components;

import custom.entity.CreateDatabaseParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateDatabaseResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CreateDatabaseComp {
    public static CreateDatabaseResult createDatabase(CreateDatabaseParams createDatabaseParams) {
        CreateDatabaseResult createDatabaseResult = CreateDatabaseResult.builder().build();
        try {
            milvusClientV2.createDatabase(CreateDatabaseReq.builder()
                    .databaseName(createDatabaseParams.getDatabaseName())
                    .build());
            createDatabaseResult.setCommonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build());
        } catch (Exception e) {
            log.info(String.format("CreateDatabase:%s", e.getMessage()));
            createDatabaseResult.setCommonResult(CommonResult.builder().result(ResultEnum.WARNING.result)
                    .message(e.getMessage()).build());
        }
        return createDatabaseResult;
    }
}
