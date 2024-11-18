package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.RollingUpgradeParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.RollingUpgradeResult;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import static custom.BaseTest.cloudServiceUserInfo;

@Slf4j
public class RollingUpgradeComp {
    public static RollingUpgradeResult rollingUpgradeInstance(RollingUpgradeParams rollingUpgradeParams){
        //查询用户状态，如果未登录，则用临时邮箱账号
        if(cloudServiceUserInfo.getUserId()==null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("") ){
            cloudServiceUserInfo= CloudServiceUtils.queryUserIdOfCloudService(null,null);
        }
        // 先查询当前实例状态
        String s = ResourceManagerServiceUtils.describeInstance();
        JSONObject jsonObject = JSONObject.parseObject(s);
        Integer status = jsonObject.getJSONObject("Data").getInteger("Status");
        InstanceStatusEnum instanceStatusByCode = InstanceStatusEnum.getInstanceStatusByCode(status);
        log.info("Current status:"+ instanceStatusByCode.toString());
        if (instanceStatusByCode.code !=InstanceStatusEnum.RUNNING.code){
            return RollingUpgradeResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message("instance status can't upgrade!").build()).build();
        }
        // 滚动升级
        String rollingUpgradeResult = ResourceManagerServiceUtils.rollingUpgrade(rollingUpgradeParams);
        JSONObject jsonObjectResult=JSONObject.parseObject(rollingUpgradeResult);
        if (jsonObjectResult.getInteger("Code")!=0){
            return RollingUpgradeResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(jsonObjectResult.getString("Message")).build()).build();
        }
        String  taskId=jsonObjectResult.getJSONObject("Data").getString("taskId");
        // 轮询结果
        int ruStatus=0;
        long startLoadTime = System.currentTimeMillis();
        try {
            Thread.sleep(1000*20);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
        LocalDateTime endTime=LocalDateTime.now().plusMinutes(30);
        while(ruStatus!=InstanceStatusEnum.RUNNING.code && LocalDateTime.now().isBefore(endTime)){
            String describeInstance = ResourceManagerServiceUtils.describeInstance();
            JSONObject descJO = JSONObject.parseObject(describeInstance);
            ruStatus = descJO.getJSONObject("Data").getInteger("Status");
            log.info("[RollingUpgrade] current status:"+ InstanceStatusEnum.getInstanceStatusByCode(ruStatus).toString());
            try {
                if(ruStatus!=InstanceStatusEnum.RUNNING.code) {
                    Thread.sleep(1000 * 10);
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }

        }
        long endLoadTime = System.currentTimeMillis();
        log.info("RollingUpgrade cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
        if (ruStatus == InstanceStatusEnum.RUNNING.code){
            return RollingUpgradeResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build())
                    .costSeconds((int) ((endLoadTime - startLoadTime) / 1000.00)).build();
        }
        return RollingUpgradeResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.WARNING.result)
                        .message("RollingUpgrade time out！").build())
                .build();
    }
}
