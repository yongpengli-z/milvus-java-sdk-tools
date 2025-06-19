package custom.components;

import custom.entity.UseDatabaseParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UseDatabaseResult;

import static custom.BaseTest.milvusClientV2;

public class UseDatabaseComp {
    public static UseDatabaseResult useDatabase(UseDatabaseParams useDataBaseParams) {
        try {
            milvusClientV2.useDatabase(useDataBaseParams.getDataBaseName());
            return UseDatabaseResult.builder().commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build()).build();
        } catch (InterruptedException e) {
            return UseDatabaseResult.builder().commonResult(CommonResult.builder().result(ResultEnum.EXCEPTION.result).message(e.getMessage()).build()).build();
        }
    }
}
