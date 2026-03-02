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

        // 构造目标 classId（replica 编码在 classId 中，如 class-8-2-enterprise）
        String baseClassId = (scaleInstanceParams.getTargetCuType() != null && !scaleInstanceParams.getTargetCuType().isEmpty())
                ? scaleInstanceParams.getTargetCuType() : currentClassId;
        int targetReplica = scaleInstanceParams.getTargetReplica();
        // 如果未指定 replica（0），保持当前值
        int finalReplica = targetReplica > 0 ? targetReplica : currentReplica;
        String targetClassId = buildClassId(baseClassId, finalReplica);
        log.info("Target classId: " + targetClassId + " (baseClassId=" + baseClassId + ", replica=" + finalReplica + ")");

        if (targetClassId.equalsIgnoreCase(currentClassId)) {
            log.info("No changes needed, classId is already " + currentClassId);
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message("No changes needed, already at target values.").build())
                    .costSeconds(0).build();
        }

        // 一次 modify 调用，classId 中已包含 replica 信息
        log.info("Scale instance: " + currentClassId + " -> " + targetClassId);
        String modifyResp = ResourceManagerServiceUtils.modifyInstance(instanceId, targetClassId, finalReplica);
        JSONObject modifyJO = JSONObject.parseObject(modifyResp);
        if (modifyJO.getInteger("Code") == null || modifyJO.getInteger("Code") != 0) {
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message("modify failed: " + modifyJO.getString("Message")).build()).build();
        }
        log.info("Submit modify success!");
        String waitResult = waitForRunning(instanceId);
        if (waitResult != null) {
            return ScaleInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message("Wait for scale timeout: " + waitResult).build()).build();
        }
        log.info("Scale completed, instance is RUNNING.");

        long endTime = System.currentTimeMillis();
        int costSeconds = (int) ((endTime - startTime) / 1000);
        log.info("ScaleInstance cost " + costSeconds + " seconds");
        return ScaleInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.SUCCESS.result).build())
                .costSeconds(costSeconds).build();
    }

    /**
     * 根据基础 classId 和 replica 数构造最终 classId。
     * 规则：replica=1 时省略，replica>=2 时插在 CU 数后面。
     * 例：class-8-enterprise + replica=2 -> class-8-2-enterprise
     *     class-8-disk-enterprise + replica=3 -> class-8-3-disk-enterprise
     *     class-8-2-enterprise + replica=1 -> class-8-enterprise（去掉 replica 部分）
     */
    private static String buildClassId(String baseClassId, int replica) {
        // 先把 baseClassId 中可能已有的 replica 数去掉，还原为 1-replica 形式
        // 格式: class-{cu}[-{replica}][-disk|-tiered]-enterprise
        String normalized = stripReplica(baseClassId);
        if (replica <= 1) {
            return normalized;
        }
        // 在 CU 数后面插入 replica: class-{cu} + -{replica} + 剩余部分
        // normalized 格式: class-{cu}-enterprise / class-{cu}-disk-enterprise / class-{cu}-tiered-enterprise
        int firstDash = normalized.indexOf("-"); // "class" 后的第一个 -
        int secondDash = normalized.indexOf("-", firstDash + 1); // CU 数后的 -
        return normalized.substring(0, secondDash) + "-" + replica + normalized.substring(secondDash);
    }

    /**
     * 去掉 classId 中的 replica 部分，还原为 1-replica 形式。
     * class-8-2-enterprise -> class-8-enterprise
     * class-8-3-disk-enterprise -> class-8-disk-enterprise
     * class-8-enterprise -> class-8-enterprise (不变)
     */
    private static String stripReplica(String classId) {
        // 格式: class-{cu}[-{replicaNum}][-disk|-tiered]-enterprise
        String[] parts = classId.split("-");
        // parts[0]=class, parts[1]=cu, ...可能有 replica 数字, 然后 disk/tiered(可选), enterprise
        // 判断 parts[2] 是否为纯数字（replica）
        if (parts.length >= 4 && parts[2].matches("\\d+")) {
            // 去掉 parts[2]（replica 部分）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i == 2) continue;
                if (sb.length() > 0) sb.append("-");
                sb.append(parts[i]);
            }
            return sb.toString();
        }
        return classId;
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
