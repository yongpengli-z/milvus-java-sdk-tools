package custom.components;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import custom.common.ComponentSchedule;
import custom.common.InstanceStatusEnum;
import custom.entity.CreateInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateInstanceResult;
import custom.entity.result.ResultEnum;
import custom.pojo.InstanceInfo;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceTestUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.InfraServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static custom.BaseTest.*;

@Slf4j
public class CreateInstanceComp {
    public static CreateInstanceResult createInstance(CreateInstanceParams createInstanceParams) {
        LocalDateTime startTime = LocalDateTime.now();
        // 登录cloudService获取账户信息 // 检查账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            if (createInstanceParams.getAccountEmail() == null || createInstanceParams.getAccountEmail().equalsIgnoreCase("")) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            } else {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(createInstanceParams.getAccountEmail(), createInstanceParams.getAccountPassword());
            }
        }        // check是否存在同名的实例
        List<InstanceInfo> instanceInfoList =
                CloudServiceUtils.listInstance();
        boolean isExist = false;
        for (InstanceInfo instanceInfo : instanceInfoList) {
            if (instanceInfo.getInstanceName().equalsIgnoreCase(createInstanceParams.getInstanceName())) {
                isExist = true;
                break;
            }
        }
        if (isExist) {
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message("The specified instance name already exists.")
                    .result(ResultEnum.EXCEPTION.result).build())
                    .createCostSeconds(costSeconds).build();
        }
        // image重新获取
        String latestImageByKeywords;
        if (createInstanceParams.getDbVersion().equalsIgnoreCase("latest-release")) {
            List<String> strings = ComponentSchedule.queryReleaseImage();
            latestImageByKeywords = strings.get(0);
            createInstanceParams.setDbVersion(latestImageByKeywords.substring(0, latestImageByKeywords.indexOf("(")));
        } else {
            latestImageByKeywords = CloudOpsServiceUtils.getLatestImageByKeywords(createInstanceParams.getDbVersion());
            createInstanceParams.setDbVersion(latestImageByKeywords.substring(0, latestImageByKeywords.indexOf("(")));
        }
        // 调用rm-service（先用 replica=1 的 classId 创建，创建成功后再通过 modify 设置 replica）
        String resp = ResourceManagerServiceUtils.createInstance(createInstanceParams);
        log.info("create instance:" + resp);
        JSONObject jsonObject = JSONObject.parseObject(resp);
        Integer code = jsonObject.getInteger("Code");
        if (code != 0) {
            log.info("create instance failed: " + jsonObject.getString("Message"));
            String failedInstanceId = extractInstanceId(jsonObject);
            if (hasInstanceId(failedInstanceId)) {
                ComponentSchedule.initInstanceStatus(failedInstanceId, "--", latestImageByKeywords, InstanceStatusEnum.CREATE_FAILED.code);
            } else {
                ComponentSchedule.initInstanceStatus("--", "--", latestImageByKeywords, InstanceStatusEnum.CREATE_FAILED.code);
            }
            return buildCreateFailedResult(createInstanceParams, failedInstanceId,
                    "create instance failed: " + jsonObject.getString("Message"), startTime);
        }
        String instanceId = extractInstanceId(jsonObject);
        if (!hasInstanceId(instanceId)) {
            ComponentSchedule.initInstanceStatus("--", "--", latestImageByKeywords, InstanceStatusEnum.CREATE_FAILED.code);
            return buildCreateFailedResult(createInstanceParams, instanceId,
                    "create instance failed: response does not contain instanceId", startTime);
        }
        log.info("Submit create instance success!");
        ComponentSchedule.initInstanceStatus(instanceId, "", latestImageByKeywords, InstanceStatusEnum.CREATING.code);
        // 判断是否需要重保
        boolean bizCriticalSuccess = false;
        if (createInstanceParams.isBizCritical()) {
            String s = ResourceManagerServiceUtils.updateLabel(instanceId);
            log.info("update biz-critical: " + s);
            bizCriticalSuccess = isApiSuccess(s);
        }
        // 判断是否需要独占
        boolean monopolizedSuccess = false;
        if (createInstanceParams.isMonopolized()) {
            String s = ResourceManagerServiceUtils.updateQNMonopoly(instanceId);
            log.info("update monopolized: " + s);
            monopolizedSuccess = isApiSuccess(s);
        }
        // 判断是否需要打散
        if (createInstanceParams.isQnBreakUp()) {
            String s = ResourceManagerServiceUtils.updateQNBreakUp(instanceId);
            log.info("update qn break up: " + s);
        }
        // replica 在创建成功后通过 modifyInstance 设置（需要等实例 RUNNING 后执行）
        //判断是否需要修改streamingNode配置--2.6 image 才行
        if (latestImageByKeywords.contains("2.6") && createInstanceParams.getStreamingNodeParams() != null) {
            // 判断是否需要修改replica
            if (createInstanceParams.getStreamingNodeParams().getReplicaNum() > 0) {
                String s = ResourceManagerServiceUtils.updateReplica(instanceId, createInstanceParams.getStreamingNodeParams().getReplicaNum(),
                        Lists.newArrayList("streamingNode"));
                log.info("update replica: " + s);
            }
            // 判断是否需要修改limits
            if (createInstanceParams.getStreamingNodeParams().getCpu() != null && !createInstanceParams.getStreamingNodeParams().getCpu().equalsIgnoreCase("")) {
                String streamingNode = ResourceManagerServiceUtils.updateLimits(instanceId,
                        createInstanceParams.getStreamingNodeParams().getCpu(),
                        createInstanceParams.getStreamingNodeParams().getMemory(),
                        createInstanceParams.getStreamingNodeParams().getDisk(),
                        Lists.newArrayList("streamingNode"));
                log.info("update limit: " + streamingNode);
            }
        }

        // 轮询是否建成功
        int waitingTime = 30;
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(waitingTime);
        boolean createSuccess = false;
        boolean deployFailed = false;
        InstanceStatusEnum lastStatus = null;
        while (LocalDateTime.now().isBefore(endTime)) {
            InstanceStatusEnum status = describeStatus(instanceId);
            if (status != null) {
                lastStatus = status;
            }
            if (isCreateFailureStatus(status)) {
                deployFailed = true;
                break;
            }
            if (status == null || status == InstanceStatusEnum.RUNNING) {
                List<InstanceInfo> instanceInfos = CloudServiceUtils.listInstance();
                if (instanceInfos.size() > 0) {
                    for (InstanceInfo instanceInfo : instanceInfos) {
                        if (instanceInfo.getInstanceName().equalsIgnoreCase(createInstanceParams.getInstanceName())) {
                            if (status == null || status == InstanceStatusEnum.RUNNING) {
                                createSuccess = true;
                                newInstanceInfo.setInstanceId(instanceInfo.getInstanceId());
                                newInstanceInfo.setUri(instanceInfo.getUri());
                                newInstanceInfo.setInstanceName(instanceInfo.getInstanceName());
                            }
                            break;
                        }
                    }
                }
            }
            if (createSuccess) {
                break;
            }
            log.info("waiting for create instance...");
            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException e) {
                log.info(e.getMessage());
            }
        }
        if (!createSuccess) {
            String failMessage = deployFailed
                    ? "实例部署失败，当前状态：" + lastStatus
                    : "轮询超时！未监测到实例创建成功，最后状态：" + (lastStatus == null ? "UNKNOWN" : lastStatus);
            log.info(failMessage);
            // 上报结果
            ComponentSchedule.updateInstanceStatus(instanceId, "--", latestImageByKeywords, InstanceStatusEnum.CREATE_FAILED.code);
            return buildCreateFailedResult(createInstanceParams, instanceId, failMessage, startTime);
        }

        int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        CreateInstanceResult createInstanceResult = CreateInstanceResult.builder()
                .createCostSeconds(costSeconds).build();
        // 根据实际 API 调用结果设置
        createInstanceResult.setBizCritical(bizCriticalSuccess);
        createInstanceResult.setMonopolized(monopolizedSuccess);
        // 初始化实例
        if (createInstanceParams.getRoleUse().equalsIgnoreCase("root")) {
            String token = MilvusConnect.provideToken(newInstanceInfo.getUri());
            newInstanceInfo.setToken(token);
        }
        if (createInstanceParams.getRoleUse().equalsIgnoreCase("db_admin")) {
            String token = "db_admin:" + createInstanceParams.getRootPassword();
            newInstanceInfo.setToken(token);
        }
        milvusClientV2 = MilvusConnect.createMilvusClientV2(newInstanceInfo.getUri(), newInstanceInfo.getToken());
        milvusClientV1 = MilvusConnect.createMilvusClientV1(newInstanceInfo.getUri(), newInstanceInfo.getToken());

        log.info("创建实例成功：" + newInstanceInfo);
        ComponentSchedule.updateInstanceStatus(newInstanceInfo.getInstanceId(), newInstanceInfo.getUri(), latestImageByKeywords, InstanceStatusEnum.RUNNING.code);

        // 创建成功后，通过 modifyInstance 设置 replica（classId 编码 replica，如 class-8-3-enterprise）
        if (createInstanceParams.getReplica() > 1) {
            String replicaClassId = ResourceManagerServiceUtils.buildClassId(
                    createInstanceParams.getCuType(), createInstanceParams.getReplica());
            log.info("Modify replica: classId=" + replicaClassId + ", replica=" + createInstanceParams.getReplica());
            String modifyResp = ResourceManagerServiceUtils.modifyInstance(
                    newInstanceInfo.getInstanceId(), replicaClassId, createInstanceParams.getReplica());
            log.info("Modify replica response: " + modifyResp);
            JSONObject modifyJO = JSONObject.parseObject(modifyResp);
            if (modifyJO.getInteger("Code") == null || modifyJO.getInteger("Code") != 0) {
                log.warn("Modify replica failed: " + modifyJO.getString("Message"));
            } else {
                log.info("Submit modify replica success, waiting for RUNNING...");
                // 等待 replica 变更完成
                LocalDateTime replicaEndTime = LocalDateTime.now().plusMinutes(30);
                int replicaStatus = 0;
                try { Thread.sleep(1000 * 20); } catch (InterruptedException e) { log.error(e.getMessage()); }
                while (replicaStatus != InstanceStatusEnum.RUNNING.code && LocalDateTime.now().isBefore(replicaEndTime)) {
                    String descResp = ResourceManagerServiceUtils.describeInstance(newInstanceInfo.getInstanceId());
                    JSONObject jo = JSONObject.parseObject(descResp);
                    replicaStatus = jo.getJSONObject("Data").getInteger("Status");
                    log.info("[CreateInstance] replica change status:" + InstanceStatusEnum.getInstanceStatusByCode(replicaStatus));
                    if (replicaStatus != InstanceStatusEnum.RUNNING.code) {
                        try { Thread.sleep(1000 * 10); } catch (InterruptedException e) { log.error(e.getMessage()); }
                    }
                }
                if (replicaStatus != InstanceStatusEnum.RUNNING.code) {
                    log.warn("Modify replica timeout!");
                } else {
                    log.info("Modify replica completed, instance is RUNNING.");
                }
            }
        }

        createInstanceResult.setCommonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build());
        createInstanceResult.setInstanceId(newInstanceInfo.getInstanceId());
        createInstanceResult.setUri(newInstanceInfo.getUri());
        return createInstanceResult;

    }

    private static boolean isApiSuccess(String response) {
        try {
            JSONObject jo = JSONObject.parseObject(response);
            return jo != null && jo.getInteger("Code") != null && jo.getInteger("Code") == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static InstanceStatusEnum describeStatus(String instanceId) {
        try {
            String descResp = ResourceManagerServiceUtils.describeInstance(instanceId);
            JSONObject jo = JSONObject.parseObject(descResp);
            if (jo == null || jo.getJSONObject("Data") == null || jo.getJSONObject("Data").getInteger("Status") == null) {
                return null;
            }
            InstanceStatusEnum status = InstanceStatusEnum.getInstanceStatusByCode(jo.getJSONObject("Data").getInteger("Status"));
            log.info("[CreateInstance] instance status: {}", status);
            return status;
        } catch (Exception e) {
            log.warn("[CreateInstance] describe instance status failed: {}", e.getMessage());
            return null;
        }
    }

    private static String cleanupCreateFailedResource(CreateInstanceParams params, String instanceId) {
        if (!params.isCleanupResourceOnCreateFailed()) {
            return null;
        }
        if (!hasInstanceId(instanceId)) {
            return "cleanup skipped: no instanceId returned";
        }
        try {
            String cleanupResp = CloudServiceTestUtils.deleteInstanceNotInMeta(instanceId);
            return "cleanup response: " + cleanupResp;
        } catch (Exception e) {
            log.warn("[CreateInstance] cleanup failed resource exception: {}", e.getMessage());
            return "cleanup exception: " + e.getMessage();
        }
    }

    private static CreateInstanceResult buildCreateFailedResult(CreateInstanceParams params, String instanceId,
                                                               String message, LocalDateTime startTime) {
        String cleanupMessage = cleanupCreateFailedResource(params, instanceId);
        int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        CreateInstanceResult result = CreateInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .message(appendCleanupMessage(message, cleanupMessage))
                        .result(ResultEnum.EXCEPTION.result).build())
                .createCostSeconds(costSeconds)
                .build();
        if (hasInstanceId(instanceId)) {
            result.setInstanceId(instanceId);
        }
        return result;
    }

    private static String appendCleanupMessage(String message, String cleanupMessage) {
        if (cleanupMessage == null || cleanupMessage.isEmpty()) {
            return message;
        }
        return message + " " + cleanupMessage;
    }

    private static boolean isCreateFailureStatus(InstanceStatusEnum status) {
        return status == InstanceStatusEnum.CREATE_FAILED || status == InstanceStatusEnum.ABNORMAL;
    }

    private static boolean hasInstanceId(String instanceId) {
        return instanceId != null && !instanceId.isEmpty() && !instanceId.equalsIgnoreCase("--");
    }

    private static String extractInstanceId(JSONObject response) {
        try {
            JSONObject data = response.getJSONObject("Data");
            return data == null ? null : data.getString("InstanceId");
        } catch (Exception e) {
            return null;
        }
    }
}
