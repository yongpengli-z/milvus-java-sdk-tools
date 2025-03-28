package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.entity.UpdateIndexPoolParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UpdateIndexPoolResult;
import custom.pojo.IndexPoolInfo;
import custom.utils.CloudOpsServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class UpdateIndexPoolComp {
    public static UpdateIndexPoolResult updateIndexPool(UpdateIndexPoolParams updateIndexPoolParams){
        IndexPoolInfo indexPoolInfo = CloudOpsServiceUtils.providerIndexPool();
        if (!(updateIndexPoolParams.getManagerImageTag()==null ||
                updateIndexPoolParams.getManagerImageTag().equalsIgnoreCase(""))){
            indexPoolInfo.setManagerImageTag(updateIndexPoolParams.getManagerImageTag());
        }
        if(!(updateIndexPoolParams.getWorkerImageTag()==null ||
                updateIndexPoolParams.getWorkerImageTag().equalsIgnoreCase(""))){
            // image重新获取
            String latestImageByKeywords;
            if(updateIndexPoolParams.getWorkerImageTag().equalsIgnoreCase("latest-release")){
                List<String> strings = ComponentSchedule.queryReleaseImage();
                latestImageByKeywords = strings.get(0);
                indexPoolInfo.setWorkerImageTag(latestImageByKeywords.substring(latestImageByKeywords.indexOf("(")+1,latestImageByKeywords.indexOf(")")));
            }else{
                latestImageByKeywords = CloudOpsServiceUtils.getLatestImageByKeywords(updateIndexPoolParams.getWorkerImageTag());
                indexPoolInfo.setWorkerImageTag(latestImageByKeywords.substring(latestImageByKeywords.indexOf("(")+1,latestImageByKeywords.indexOf(")")));
            }
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
