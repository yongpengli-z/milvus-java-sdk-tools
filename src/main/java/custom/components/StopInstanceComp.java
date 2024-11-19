package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.StopInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.StopInstanceResult;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import static custom.BaseTest.cloudServiceUserInfo;

@Slf4j
public class StopInstanceComp {
    public static StopInstanceResult stopInstance(StopInstanceParams stopInstanceParams) {
        // 检查账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            if (stopInstanceParams.getAccountEmail() == null || stopInstanceParams.getAccountEmail().equalsIgnoreCase("")) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            } else {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(stopInstanceParams.getAccountEmail(), stopInstanceParams.getAccountPassword());
            }
        }

        // 检查实例状态
        String describeInstance = ResourceManagerServiceUtils.describeInstance(stopInstanceParams.getInstanceId());
        JSONObject jsonObject = JSONObject.parseObject(describeInstance);
        Integer status = jsonObject.getJSONObject("Data").getInteger("Status");
        InstanceStatusEnum instanceStatusByCode = InstanceStatusEnum.getInstanceStatusByCode(status);
        log.info("Current status:" + instanceStatusByCode.toString());
        if (instanceStatusByCode.code != InstanceStatusEnum.RUNNING.code) {
            return StopInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message("instance status can't stop!"+"Current status:" + instanceStatusByCode).build()).build();
        }

        // stop
        String stopResult = CloudServiceUtils.stopInstance(stopInstanceParams);
        JSONObject stopJO = JSONObject.parseObject(stopResult);
        if (stopJO.getInteger("Code") != 0) {
            return StopInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message(stopJO.getString("Message")).build()).build();
        }
        // 轮询结果
        int ruStatus;
        long startLoadTime = System.currentTimeMillis();
        try {
            Thread.sleep(1000 * 20);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(30);
        do {
            String descResult = ResourceManagerServiceUtils.describeInstance(stopInstanceParams.getInstanceId());
            JSONObject descJO = JSONObject.parseObject(descResult);
            ruStatus = descJO.getJSONObject("Data").getInteger("Status");
            InstanceStatusEnum instanceStatusE = InstanceStatusEnum.getInstanceStatusByCode(ruStatus);
            log.info("Current instance status:" + instanceStatusE.toString());
            try {
                if (ruStatus != InstanceStatusEnum.STOPPED.code) {
                    Thread.sleep(1000 * 10);
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        } while (ruStatus != InstanceStatusEnum.STOPPED.code && LocalDateTime.now().isBefore(endTime));
        long endLoadTime = System.currentTimeMillis();
        log.info("Stop instance cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");

        if (ruStatus == InstanceStatusEnum.STOPPED.code){
            return StopInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build())
                    .costSeconds((int) ((endLoadTime - startLoadTime) / 1000.00)).build();
        }
        return StopInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.WARNING.result)
                        .message("Stop instance time out！").build())
                .build();
    }


}
