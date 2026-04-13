package custom.components;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.common.InstanceStatusEnum;
import custom.entity.CreateGlobalClusterParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateGlobalClusterResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import custom.pojo.InstanceInfo;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.*;

@Slf4j
public class CreateGlobalClusterComp {

    public static CreateGlobalClusterResult createGlobalCluster(CreateGlobalClusterParams params) {
        LocalDateTime startTime = LocalDateTime.now();

        // 1. 登录检查
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().isEmpty()) {
            if (params.getAccountEmail() == null || params.getAccountEmail().isEmpty()) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            } else {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(
                        params.getAccountEmail(), params.getAccountPassword());
            }
        }

        // 2. image 版本解析
        String latestImage;
        if (params.getDbVersion().equalsIgnoreCase("latest-release")) {
            List<String> releaseImages = ComponentSchedule.queryReleaseImage();
            latestImage = releaseImages.get(0);
            params.setDbVersion(latestImage.substring(0, latestImage.indexOf("(")));
        } else {
            latestImage = CloudOpsServiceUtils.getLatestImageByKeywords(params.getDbVersion());
            params.setDbVersion(latestImage.substring(0, latestImage.indexOf("(")));
        }

        // 3. 调用 RM 创建 Global Cluster
        String resp = ResourceManagerServiceUtils.createGlobalCluster(params);
        log.info("create global cluster resp: {}", resp);
        JSONObject jsonObject = JSONObject.parseObject(resp);
        Integer code = jsonObject.getInteger("Code");
        if (code == null || code != 0) {
            log.error("create global cluster failed: {}", jsonObject.getString("Message"));
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            return CreateGlobalClusterResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(jsonObject.getString("Message")).build())
                    .createCostSeconds(costSeconds).build();
        }

        // 4. 解析响应
        JSONObject data = jsonObject.getJSONObject("Data");
        String globalClusterId = data.getString("GlobalClusterId");
        String primaryInstanceId = data.getString("InstanceId");
        List<String> secondaryInstanceIds = new ArrayList<>();
        JSONArray secondariesArr = data.getJSONArray("SecondaryClusters");
        if (secondariesArr != null) {
            for (int i = 0; i < secondariesArr.size(); i++) {
                secondaryInstanceIds.add(secondariesArr.getJSONObject(i).getString("instanceId"));
            }
        }

        log.info("Submit create global cluster success! globalClusterId={}, primaryInstanceId={}, secondaryInstanceIds={}",
                globalClusterId, primaryInstanceId, secondaryInstanceIds);
        ComponentSchedule.initInstanceStatus(primaryInstanceId, "", latestImage, InstanceStatusEnum.CREATING.code);

        // 5. 收集所有需要等待的实例 ID
        List<String> allInstanceIds = new ArrayList<>();
        allInstanceIds.add(primaryInstanceId);
        allInstanceIds.addAll(secondaryInstanceIds);

        // 6. 轮询等待所有实例 RUNNING，并收集 URI
        int waitMinutes = params.getWaitTimeoutMinutes();
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(waitMinutes);
        Map<String, String> instanceUriMap = new HashMap<>(); // instanceId -> ConnectAddress
        Map<String, Boolean> instanceReady = new HashMap<>();
        for (String id : allInstanceIds) {
            instanceReady.put(id, false);
        }

        while (LocalDateTime.now().isBefore(endTime)) {
            boolean allRunning = true;
            for (String instanceId : allInstanceIds) {
                if (instanceReady.get(instanceId)) {
                    continue; // 已经 RUNNING 的不再查
                }
                String descResp = ResourceManagerServiceUtils.describeInstance(instanceId);
                JSONObject descJO = JSONObject.parseObject(descResp);
                if (descJO.getInteger("Code") != null && descJO.getInteger("Code") == 0) {
                    int status = descJO.getJSONObject("Data").getInteger("Status");
                    String role = instanceId.equals(primaryInstanceId) ? "primary" : "secondary";
                    log.info("[CreateGlobalCluster] {} instance {} status: {}",
                            role, instanceId, InstanceStatusEnum.getInstanceStatusByCode(status));
                    if (status == InstanceStatusEnum.RUNNING.code) {
                        instanceReady.put(instanceId, true);
                        String uri = descJO.getJSONObject("Data").getString("ConnectAddress");
                        instanceUriMap.put(instanceId, uri != null ? uri : "");
                    } else {
                        allRunning = false;
                    }
                } else {
                    allRunning = false;
                }
            }
            if (allRunning) {
                break;
            }
            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }

        int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());

        // 检查是否全部 RUNNING
        List<String> failedInstances = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : instanceReady.entrySet()) {
            if (!entry.getValue()) {
                failedInstances.add(entry.getKey());
            }
        }

        if (!failedInstances.isEmpty()) {
            String msg = "轮询超时！以下实例未达到 RUNNING 状态: " + failedInstances;
            log.error(msg);
            ComponentSchedule.updateInstanceStatus(primaryInstanceId, "--", latestImage, InstanceStatusEnum.CREATE_FAILED.code);
            return CreateGlobalClusterResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(msg).build())
                    .globalClusterId(globalClusterId)
                    .instanceId(primaryInstanceId)
                    .primaryUri(instanceUriMap.getOrDefault(primaryInstanceId, ""))
                    .secondaryInstanceIds(secondaryInstanceIds)
                    .secondaryUris(buildSecondaryUriMap(secondaryInstanceIds, instanceUriMap))
                    .createCostSeconds(costSeconds).build();
        }

        // 7. 设置 primary 实例信息到全局变量
        String primaryUri = instanceUriMap.get(primaryInstanceId);
        newInstanceInfo.setInstanceId(primaryInstanceId);
        newInstanceInfo.setUri(primaryUri != null ? primaryUri : "");
        newInstanceInfo.setInstanceName(params.getInstanceName());
        primaryInstanceInfo.setInstanceId(primaryInstanceId);
        primaryInstanceInfo.setUri(primaryUri != null ? primaryUri : "");
        primaryInstanceInfo.setInstanceName(params.getInstanceName());
        primaryInstanceInfo.setToken(newInstanceInfo.getToken());

        // 8. 填充 globalClusterInfo 和 secondaryInstanceInfoList
        globalClusterInfo.setInstanceId(globalClusterId);
        globalClusterInfo.setInstanceName(params.getInstanceName());
        globalClusterInfo.setUri(primaryUri != null ? primaryUri : "");

        secondaryInstanceInfoList.clear();
        for (String secId : secondaryInstanceIds) {
            InstanceInfo secInfo = new InstanceInfo();
            secInfo.setInstanceId(secId);
            secInfo.setUri(instanceUriMap.getOrDefault(secId, ""));
            secondaryInstanceInfoList.add(secInfo);
        }

        Map<String, String> secondaryUris = buildSecondaryUriMap(secondaryInstanceIds, instanceUriMap);

        log.info("Global Cluster 全部创建成功: globalClusterId={}, primaryUri={}, secondaryUris={}",
                globalClusterId, primaryUri, secondaryUris);
        ComponentSchedule.updateInstanceStatus(primaryInstanceId, newInstanceInfo.getUri(), latestImage, InstanceStatusEnum.RUNNING.code);

        return CreateGlobalClusterResult.builder()
                .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                .globalClusterId(globalClusterId)
                .instanceId(primaryInstanceId)
                .primaryUri(primaryUri)
                .secondaryInstanceIds(secondaryInstanceIds)
                .secondaryUris(secondaryUris)
                .createCostSeconds(costSeconds).build();
    }

    private static Map<String, String> buildSecondaryUriMap(List<String> secondaryInstanceIds,
                                                             Map<String, String> instanceUriMap) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : secondaryInstanceIds) {
            result.put(id, instanceUriMap.getOrDefault(id, ""));
        }
        return result;
    }
}
