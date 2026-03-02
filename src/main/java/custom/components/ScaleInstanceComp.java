package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.ScaleInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.ScaleInstanceResult;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import static custom.BaseTest.*;

@Slf4j
public class ScaleInstanceComp {
    public static ScaleInstanceResult scaleInstance(ScaleInstanceParams scaleInstanceParams) {
        long startTime = System.currentTimeMillis();
        // 检查账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            if (scaleInstanceParams.getAccountEmail() == null || scaleInstanceParams.getAccountEmail().equalsIgnoreCase("")) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            } else {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(scaleInstanceParams.getAccountEmail(), scaleInstanceParams.getAccountPassword());
            }
        }
        String instanceId = (scaleInstanceParams.getInstanceId() == null || scaleInstanceParams.getInstanceId().equalsIgnoreCase(""))
                ? newInstanceInfo.getInstanceId() : scaleInstanceParams.getInstanceId();
        // 先查询当前实例状态和配置
        String descResp = ResourceManagerServiceUtils.describeInstance(instanceId);
        log.info("describe instance:" + descResp);
        JSONObject descJO = JSONObject.parseObject(descResp);
        JSONObject dataJO = descJO.getJSONObject("Data");
        Integer status = dataJO.getInteger("Status");
        InstanceStatusEnum currentStatus = InstanceStatusEnum.getInstanceStatusByCode(status);
        log.info("Current status:" + currentStatus.toString());
        if (currentStatus.code != InstanceStatusEnum.RUNNING.code) {
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message("instance status is not RUNNING, can't scale!").build()).build();
        }
        String currentClassId = dataJO.getString("ClassId");
        int currentReplica = dataJO.getInteger("Replica");
        log.info("Current classId:" + currentClassId + ", current replica:" + currentReplica);

        // 对比目标值与当前值
        String targetCuType = scaleInstanceParams.getTargetCuType();
        int targetReplica = scaleInstanceParams.getTargetReplica();
        boolean cuNeedChange = targetCuType != null && !targetCuType.isEmpty() && !targetCuType.equalsIgnoreCase(currentClassId);
        boolean replicaNeedChange = targetReplica > 0 && targetReplica != currentReplica;

        if (!cuNeedChange && !replicaNeedChange) {
            log.info("No changes needed, CU and replica are already at target values.");
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message("No changes needed, already at target values.").build())
                    .costSeconds(0).build();
        }

        // 使用 modify 接口，支持同时传 classId 和 replica
        String modifyClassId = cuNeedChange ? targetCuType : null;
        int modifyReplica = replicaNeedChange ? targetReplica : 0;
        if (cuNeedChange) {
            log.info("Scale CU: " + currentClassId + " -> " + targetCuType);
        }
        if (replicaNeedChange) {
            log.info("Scale replica: " + currentReplica + " -> " + targetReplica);
        }
        String modifyResp = ResourceManagerServiceUtils.modifyInstance(instanceId, modifyClassId, modifyReplica);
        JSONObject modifyJO = JSONObject.parseObject(modifyResp);
        if (modifyJO.getInteger("Code") == null || modifyJO.getInteger("Code") != 0) {
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message("modify instance failed: " + modifyJO.getString("Message")).build()).build();
        }
        log.info("Submit modify instance success!");

        // 轮询等待实例恢复 RUNNING
        String waitResult = waitForRunning(instanceId);
        if (waitResult != null) {
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message(waitResult).build()).build();
        }

        long endTime = System.currentTimeMillis();
        int costSeconds = (int) ((endTime - startTime) / 1000);
        log.info("ScaleInstance cost " + costSeconds + " seconds");
        return ScaleInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.SUCCESS.result).build())
                .costSeconds(costSeconds).build();
    }

    /**
     * 轮询等待实例恢复 RUNNING 状态
     *
     * @return null 表示成功，非 null 表示超时错误信息
     */
    private static String waitForRunning(String instanceId) {
        try {
            Thread.sleep(1000 * 20);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(30);
        int scaleStatus = 0;
        while (scaleStatus != InstanceStatusEnum.RUNNING.code && LocalDateTime.now().isBefore(endTime)) {
            String describeInstance = ResourceManagerServiceUtils.describeInstance(instanceId);
            JSONObject jo = JSONObject.parseObject(describeInstance);
            scaleStatus = jo.getJSONObject("Data").getInteger("Status");
            log.info("[ScaleInstance] current status:" + InstanceStatusEnum.getInstanceStatusByCode(scaleStatus).toString());
            try {
                if (scaleStatus != InstanceStatusEnum.RUNNING.code) {
                    Thread.sleep(1000 * 10);
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }
        if (scaleStatus != InstanceStatusEnum.RUNNING.code) {
            return "ScaleInstance time out!";
        }
        log.info("[ScaleInstance] completed successfully.");
        return null;
    }
}
