package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.RestartInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.RestartInstanceResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import static custom.BaseTest.cloudServiceUserInfo;

@Slf4j
public class RestartInstanceComp {
    public static RestartInstanceResult restartInstance(RestartInstanceParams restartInstanceParams) {
// 检查账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            if (restartInstanceParams.getAccountEmail() == null || restartInstanceParams.getAccountEmail().equalsIgnoreCase("")) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            } else {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(restartInstanceParams.getAccountEmail(), restartInstanceParams.getAccountPassword());
            }
        }
        // 检查实例状态
        String describeInstance = ResourceManagerServiceUtils.describeInstance(restartInstanceParams.getInstanceId());
        JSONObject jsonObject = JSONObject.parseObject(describeInstance);
        Integer status = jsonObject.getJSONObject("Data").getInteger("Status");
        InstanceStatusEnum instanceStatusByCode = InstanceStatusEnum.getInstanceStatusByCode(status);
        log.info("Current status:" + instanceStatusByCode.toString());
        if (instanceStatusByCode.code != InstanceStatusEnum.RUNNING.code) {
            return RestartInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message("instance status can't restart !" + "Current status:" + instanceStatusByCode).build()).build();
        }

        // restart
        String s = ResourceManagerServiceUtils.restartInstance(restartInstanceParams.getInstanceId());
        JSONObject stopJO = JSONObject.parseObject(s);
        if (stopJO.getInteger("Code") != 0) {
            return RestartInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message(stopJO.getString("Message")).build()).build();
        }

        //轮询结果
        int ruStatus;
        long startLoadTime = System.currentTimeMillis();
        try {
            Thread.sleep(1000 * 10);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(30);
        do {
            String descResult = ResourceManagerServiceUtils.describeInstance(restartInstanceParams.getInstanceId());
            JSONObject descJO = JSONObject.parseObject(descResult);
            ruStatus = descJO.getJSONObject("Data").getInteger("Status");
            InstanceStatusEnum instanceStatusE = InstanceStatusEnum.getInstanceStatusByCode(ruStatus);
            log.info("Current instance status:" + instanceStatusE.toString());
            try {
                if (ruStatus != InstanceStatusEnum.RUNNING.code) {
                    Thread.sleep(1000 * 10);
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        } while (ruStatus != InstanceStatusEnum.RUNNING.code && LocalDateTime.now().isBefore(endTime));
        long endLoadTime = System.currentTimeMillis();
        log.info("Resume instance cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");

        if (ruStatus == InstanceStatusEnum.RUNNING.code) {
            return RestartInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build())
                    .costSeconds((int) ((endLoadTime - startLoadTime) / 1000.00)).build();
        }
        return RestartInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.WARNING.result)
                        .message("Restart instance time out！").build())
                .build();
    }
}
