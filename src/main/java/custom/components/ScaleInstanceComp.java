package custom.components;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
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

        // 修改 CU class（对比当前值）
        boolean cuChanged = false;
        if (scaleInstanceParams.getTargetCuType() != null && !scaleInstanceParams.getTargetCuType().equalsIgnoreCase("")
                && !scaleInstanceParams.getTargetCuType().equalsIgnoreCase(currentClassId)) {
            log.info("Scale CU: " + currentClassId + " -> " + scaleInstanceParams.getTargetCuType());
            String modifyResp = ResourceManagerServiceUtils.modifyInstance(instanceId, scaleInstanceParams.getTargetCuType());
            JSONObject modifyJO = JSONObject.parseObject(modifyResp);
            if (modifyJO.getInteger("Code") == null || modifyJO.getInteger("Code") != 0) {
                return ScaleInstanceResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message("modify CU failed: " + modifyJO.getString("Message")).build()).build();
            }
            log.info("Submit modify CU success!");
            cuChanged = true;
            // 轮询等待 CU 变更完成（实例恢复 RUNNING）
            String waitResult = waitForRunning(instanceId, "modify CU");
            if (waitResult != null) {
                return ScaleInstanceResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.WARNING.result)
                                .message(waitResult).build()).build();
            }
        } else {
            log.info("CU type unchanged, skip modify CU.");
        }

        // 修改 replica（对比当前值）
        boolean replicaChanged = false;
        if (scaleInstanceParams.getTargetReplica() > 0 && scaleInstanceParams.getTargetReplica() != currentReplica) {
            log.info("Scale replica: " + currentReplica + " -> " + scaleInstanceParams.getTargetReplica());
            String replicaResp = ResourceManagerServiceUtils.updateReplica(instanceId, scaleInstanceParams.getTargetReplica(),
                    Lists.newArrayList("queryNode"));
            JSONObject replicaJO = JSONObject.parseObject(replicaResp);
            if (replicaJO.getInteger("Code") == null || replicaJO.getInteger("Code") != 0) {
                return ScaleInstanceResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message("modify replica failed: " + replicaJO.getString("Message")).build()).build();
            }
            log.info("Submit modify replica success!");
            replicaChanged = true;
            // 轮询等待 replica 变更完成
            String waitResult = waitForRunning(instanceId, "modify replica");
            if (waitResult != null) {
                return ScaleInstanceResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.WARNING.result)
                                .message(waitResult).build()).build();
            }
        } else {
            log.info("Replica unchanged, skip modify replica.");
        }

        long endTime = System.currentTimeMillis();
        int costSeconds = (int) ((endTime - startTime) / 1000);

        if (!cuChanged && !replicaChanged) {
            log.info("No changes needed, CU and replica are already at target values.");
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message("No changes needed, already at target values.").build())
                    .costSeconds(costSeconds).build();
        }

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
    private static String waitForRunning(String instanceId, String operation) {
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
            log.info("[ScaleInstance][" + operation + "] current status:" + InstanceStatusEnum.getInstanceStatusByCode(scaleStatus).toString());
            try {
                if (scaleStatus != InstanceStatusEnum.RUNNING.code) {
                    Thread.sleep(1000 * 10);
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }
        if (scaleStatus != InstanceStatusEnum.RUNNING.code) {
            return "ScaleInstance [" + operation + "] time out!";
        }
        log.info("[ScaleInstance][" + operation + "] completed successfully.");
        return null;
    }
}
