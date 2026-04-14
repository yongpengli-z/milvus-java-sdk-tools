package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.entity.AlterInstanceIndexClusterParams;
import custom.entity.RestartInstanceParams;
import custom.entity.result.AlterInstanceIndexClusterResult;
import custom.entity.result.CommonResult;
import custom.entity.result.RestartInstanceResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceUtils;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class AlterInstanceIndexClusterComp {
    public static AlterInstanceIndexClusterResult alterIndexCluster(AlterInstanceIndexClusterParams alterInstanceIndexClusterParams){
        // 检查账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
        }
        String s = CloudOpsServiceUtils.alterIndexCluster(alterInstanceIndexClusterParams);
        JSONObject jsonObject = JSONObject.parseObject(s);
        Integer code = jsonObject.getInteger("code");
        CommonResult.CommonResultBuilder commonResultBuilder = CommonResult.builder();
        if (code==0){
            String instanceId = alterInstanceIndexClusterParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : alterInstanceIndexClusterParams.getInstanceId();

            // 内嵌重启
            if (alterInstanceIndexClusterParams.isNeedRestart()) {
                log.info("[AlterIndexCluster] alter success, start restart instance: " + instanceId);
                RestartInstanceParams restartInstanceParams = new RestartInstanceParams();
                restartInstanceParams.setInstanceId(instanceId);
                RestartInstanceResult restartResult = RestartInstanceComp.restartInstance(restartInstanceParams);
                if (restartResult.getCommonResult() == null
                        || !ResultEnum.SUCCESS.result.equalsIgnoreCase(restartResult.getCommonResult().getResult())) {
                    String msg = restartResult.getCommonResult() != null ? restartResult.getCommonResult().getMessage() : "unknown error";
                    log.warn("[AlterIndexCluster] restart failed: " + msg);
                    commonResultBuilder.result(ResultEnum.WARNING.result)
                            .message("AlterIndexCluster success but restart failed: " + msg);
                    return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                            .instanceId(instanceId)
                            .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                            .build();
                }
                log.info("[AlterIndexCluster] restart success, cost " + restartResult.getCostSeconds() + "s");
            }

            commonResultBuilder.result(ResultEnum.SUCCESS.result);
            return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                    .instanceId(instanceId)
                    .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                    .build();
        }else {
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message(jsonObject.getString("message"));
            return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build()).build();
        }
    }
}
