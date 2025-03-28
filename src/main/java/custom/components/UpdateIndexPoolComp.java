package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.entity.UpdateIndexPoolParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UpdateIndexPoolResult;
import custom.pojo.IndexPoolInfo;
import custom.utils.CloudOpsServiceUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateIndexPoolComp {
    public static UpdateIndexPoolResult updateIndexPool(UpdateIndexPoolParams updateIndexPoolParams){
        IndexPoolInfo indexPoolInfo = CloudOpsServiceUtils.providerIndexPool();
        if (!(updateIndexPoolParams.getWorkerImageTag()==null ||
                updateIndexPoolParams.getWorkerImageTag().equalsIgnoreCase(""))){
            indexPoolInfo.setManagerImageTag(updateIndexPoolParams.getManagerImageTag());
        }
        if(!(updateIndexPoolParams.getWorkerImageTag()==null ||
                updateIndexPoolParams.getWorkerImageTag().equalsIgnoreCase(""))){
            indexPoolInfo.setWorkerImageTag(updateIndexPoolParams.getWorkerImageTag());
        }
        String s = CloudOpsServiceUtils.updateIndexPool(indexPoolInfo);
        JSONObject jsonObject = JSONObject.parseObject(s);
        Integer code = jsonObject.getInteger("code");
        CommonResult.CommonResultBuilder commonResultBuilder = CommonResult.builder();
        if (code==0){
            commonResultBuilder.result(ResultEnum.SUCCESS.result);
        }else {
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message(jsonObject.getString("message"));
        }
        return UpdateIndexPoolResult.builder()
                .commonResult(commonResultBuilder.build())
                .currentManagerImage(indexPoolInfo.getManagerImageTag())
                .currentWorkerImage(indexPoolInfo.getWorkerImageTag())
                .build();
    }
}
