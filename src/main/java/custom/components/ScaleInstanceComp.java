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

        // replica 编码在 classId 中（如 class-8-2-enterprise），RM 不允许同时改 CU 和 replica
        String baseCuType = (scaleInstanceParams.getTargetCuType() != null && !scaleInstanceParams.getTargetCuType().isEmpty())
                ? scaleInstanceParams.getTargetCuType() : null;
        int targetReplica = scaleInstanceParams.getTargetReplica();
        int finalReplica = targetReplica > 0 ? targetReplica : currentReplica;

        // 判断 CU 是否需要变更（比较去掉 replica 后的基础 classId）
        String currentBaseCu = ResourceManagerServiceUtils.stripReplica(currentClassId);
        String targetBaseCu = baseCuType != null ? ResourceManagerServiceUtils.stripReplica(baseCuType) : currentBaseCu;
        boolean cuNeedChange = !targetBaseCu.equalsIgnoreCase(currentBaseCu);
        boolean replicaNeedChange = finalReplica != currentReplica;

        // replica > 1 时，CU 必须 >= 8
        if (finalReplica > 1) {
            int targetCu = ResourceManagerServiceUtils.extractCu(targetBaseCu);
            if (targetCu < 8) {
                return ScaleInstanceResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.WARNING.result)
                                .message("replica > 1 requires CU >= 8, but target CU is " + targetCu).build()).build();
            }
        }

        if (!cuNeedChange && !replicaNeedChange) {
            log.info("No changes needed, CU and replica are already at target values.");
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message("No changes needed, already at target values.").build())
                    .costSeconds(0).build();
        }

        // 决定操作顺序：降配时先减 replica 再降 CU，升配时先升 CU 再加 replica
        // 原因：中间状态也必须满足 replica>1 时 CU>=8 的约束
        int targetCu = ResourceManagerServiceUtils.extractCu(targetBaseCu);
        int currentCu = ResourceManagerServiceUtils.extractCu(currentBaseCu);
        boolean scaleDown = targetCu < currentCu;

        if (scaleDown) {
            // 降配：先减 replica，再降 CU
            if (replicaNeedChange) {
                String errMsg = doModifyReplica(instanceId, currentBaseCu, currentReplica, finalReplica);
                if (errMsg != null) return parseError(errMsg, "replica");
            }
            if (cuNeedChange) {
                String errMsg = doModifyCu(instanceId, currentClassId, targetBaseCu, finalReplica);
                if (errMsg != null) return parseError(errMsg, "CU");
            }
        } else {
            // 升配或仅改 replica：先升 CU，再加 replica
            if (cuNeedChange) {
                String errMsg = doModifyCu(instanceId, currentClassId, targetBaseCu, currentReplica);
                if (errMsg != null) return parseError(errMsg, "CU");
            }
            if (replicaNeedChange) {
                String errMsg = doModifyReplica(instanceId, targetBaseCu, currentReplica, finalReplica);
                if (errMsg != null) return parseError(errMsg, "replica");
            }
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
     * 执行 CU 变更（replica 保持不变）
     * @return null 表示成功，非 null 表示错误信息
     */
    private static String doModifyCu(String instanceId, String currentClassId, String targetBaseCu, int keepReplica) {
        String cuClassId = ResourceManagerServiceUtils.buildClassId(targetBaseCu, keepReplica);
        log.info("Scale CU: " + currentClassId + " -> " + cuClassId);
        String modifyResp = ResourceManagerServiceUtils.modifyInstance(instanceId, cuClassId, keepReplica);
        JSONObject modifyJO = JSONObject.parseObject(modifyResp);
        if (modifyJO.getInteger("Code") == null || modifyJO.getInteger("Code") != 0) {
            return modifyJO.getString("Message");
        }
        log.info("Submit modify CU success!");
        return waitForRunning(instanceId);
    }

    /**
     * 执行 replica 变更（CU 保持不变）
     * @return null 表示成功，非 null 表示错误信息
     */
    private static String doModifyReplica(String instanceId, String baseCu, int currentReplica, int targetReplica) {
        String replicaClassId = ResourceManagerServiceUtils.buildClassId(baseCu, targetReplica);
        log.info("Scale replica: " + currentReplica + " -> " + targetReplica + ", classId: " + replicaClassId);
        String modifyResp = ResourceManagerServiceUtils.modifyInstance(instanceId, replicaClassId, targetReplica);
        JSONObject modifyJO = JSONObject.parseObject(modifyResp);
        if (modifyJO.getInteger("Code") == null || modifyJO.getInteger("Code") != 0) {
            return modifyJO.getString("Message");
        }
        log.info("Submit modify replica success!");
        return waitForRunning(instanceId);
    }

    private static ScaleInstanceResult parseError(String errMsg, String operation) {
        // waitForRunning 超时返回的消息包含 "time out"
        if (errMsg.contains("time out")) {
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message("Wait for " + operation + " change timeout: " + errMsg).build()).build();
        }
        return ScaleInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message("modify " + operation + " failed: " + errMsg).build()).build();
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
