package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.entity.AlterInstanceIndexClusterParams;
import custom.entity.result.AlterInstanceIndexClusterResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceUtils;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

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
            commonResultBuilder.result(ResultEnum.SUCCESS.result);
            return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                    .instanceId(alterInstanceIndexClusterParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : alterInstanceIndexClusterParams.getInstanceId())
                    .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                    .build();
        }else {
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message(jsonObject.getString("message"));
            return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build()).build();
        }
    }
}
