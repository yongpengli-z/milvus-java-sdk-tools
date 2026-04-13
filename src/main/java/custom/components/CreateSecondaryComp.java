package custom.components;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.CreateSecondaryParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateSecondaryResult;
import custom.entity.result.ResultEnum;
import custom.pojo.InstanceInfo;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import io.milvus.v2.client.globalcluster.ClusterInfo;
import io.milvus.v2.client.globalcluster.GlobalClusterUtils;
import io.milvus.v2.client.globalcluster.GlobalTopology;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.*;

@Slf4j
public class CreateSecondaryComp {

    public static CreateSecondaryResult createSecondary(CreateSecondaryParams params) {
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

        // 2. 自动回填 instanceId / globalClusterId
        //    场景 A：用户不传 instanceId 且不传 globalClusterId → 从 newInstanceInfo 取
        //    场景 B：用户传了 globalClusterId → 直接用
        if ((params.getInstanceId() == null || params.getInstanceId().isEmpty())
                && (params.getGlobalClusterId() == null || params.getGlobalClusterId().isEmpty())) {
            // 优先用已有的 globalClusterInfo
            if (globalClusterInfo.getInstanceId() != null && !globalClusterInfo.getInstanceId().isEmpty()) {
                params.setGlobalClusterId(globalClusterInfo.getInstanceId());
                log.info("自动回填 globalClusterId={} (from globalClusterInfo)", globalClusterInfo.getInstanceId());
            } else if (newInstanceInfo.getInstanceId() != null && !newInstanceInfo.getInstanceId().isEmpty()) {
                params.setInstanceId(newInstanceInfo.getInstanceId());
                log.info("自动回填 instanceId={} (from newInstanceInfo)", newInstanceInfo.getInstanceId());
            }
        }

        if ((params.getInstanceId() == null || params.getInstanceId().isEmpty())
                && (params.getGlobalClusterId() == null || params.getGlobalClusterId().isEmpty())) {
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            return CreateSecondaryResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message("instanceId 和 globalClusterId 均为空，无法创建 secondary").build())
                    .createCostSeconds(costSeconds).build();
        }

        // 3. 调用 RM create_secondary
        String resp = ResourceManagerServiceUtils.createSecondary(params);
        log.info("create secondary resp: {}", resp);
        JSONObject jsonObject = JSONObject.parseObject(resp);
        Integer code = jsonObject.getInteger("Code");
        if (code == null || code != 0) {
            log.error("create secondary failed: {}", jsonObject.getString("Message"));
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            return CreateSecondaryResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(jsonObject.getString("Message")).build())
                    .createCostSeconds(costSeconds).build();
        }

        // 4. 解析响应
        JSONObject data = jsonObject.getJSONObject("Data");
        String globalClusterId = data.getString("GlobalClusterId");
        List<String> newSecondaryIds = new ArrayList<>();
        JSONArray secondariesArr = data.getJSONArray("SecondaryClusters");
        if (secondariesArr != null) {
            for (int i = 0; i < secondariesArr.size(); i++) {
                newSecondaryIds.add(secondariesArr.getJSONObject(i).getString("instanceId"));
            }
        }

        log.info("Submit create secondary success! globalClusterId={}, newSecondaryIds={}",
                globalClusterId, newSecondaryIds);

        // 5. 轮询等待新 secondary 实例 RUNNING
        int waitMinutes = params.getWaitTimeoutMinutes();
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(waitMinutes);
        Map<String, String> instanceUriMap = new HashMap<>();
        Map<String, Boolean> instanceReady = new HashMap<>();
        for (String id : newSecondaryIds) {
            instanceReady.put(id, false);
        }

        while (LocalDateTime.now().isBefore(endTime)) {
            boolean allRunning = true;
            for (String instanceId : newSecondaryIds) {
                if (instanceReady.get(instanceId)) {
                    continue;
                }
                String descResp = ResourceManagerServiceUtils.describeInstance(instanceId);
                JSONObject descJO = JSONObject.parseObject(descResp);
                if (descJO.getInteger("Code") != null && descJO.getInteger("Code") == 0) {
                    int status = descJO.getJSONObject("Data").getInteger("Status");
                    log.info("[CreateSecondary] secondary instance {} status: {}",
                            instanceId, InstanceStatusEnum.getInstanceStatusByCode(status));
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
            String msg = "轮询超时！以下 secondary 实例未达到 RUNNING 状态: " + failedInstances;
            log.error(msg);
            return CreateSecondaryResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(msg).build())
                    .globalClusterId(globalClusterId)
                    .newSecondaryInstanceIds(newSecondaryIds)
                    .newSecondaryUris(buildUriMap(newSecondaryIds, instanceUriMap))
                    .createCostSeconds(costSeconds).build();
        }

        // 6. 填充全局变量
        globalClusterInfo.setInstanceId(globalClusterId);

        // 通过 topology 获取完整信息（primary + 所有 secondary + global endpoint）
        String globalEndpoint = null;
        try {
            String gcEndpoint = ResourceManagerServiceUtils.describeGlobalClusterEndpoint(globalClusterId);
            if (gcEndpoint != null && !gcEndpoint.isEmpty()) {
                globalClusterInfo.setUri(gcEndpoint);
                globalEndpoint = gcEndpoint;

                GlobalTopology topology = GlobalClusterUtils.fetchTopology(gcEndpoint, newInstanceInfo.getToken());
                ClusterInfo primaryCluster = topology.getPrimary();
                String primaryEndpoint = primaryCluster.getEndpoint();
                if (!primaryEndpoint.startsWith("https://")) {
                    primaryEndpoint = "https://" + primaryEndpoint;
                }
                primaryInstanceInfo.setInstanceId(primaryCluster.getClusterId());
                primaryInstanceInfo.setUri(primaryEndpoint);
                primaryInstanceInfo.setToken(newInstanceInfo.getToken());
                log.info("Global Cluster primary: id={}, endpoint={}", primaryCluster.getClusterId(), primaryEndpoint);

                // 刷新 secondaryInstanceInfoList（包含所有 secondary，不仅是本次新增的）
                secondaryInstanceInfoList.clear();
                for (ClusterInfo cluster : topology.getClusters()) {
                    if (!cluster.isPrimary()) {
                        InstanceInfo secInfo = new InstanceInfo();
                        secInfo.setInstanceId(cluster.getClusterId());
                        String secEndpoint = cluster.getEndpoint();
                        if (!secEndpoint.startsWith("https://")) {
                            secEndpoint = "https://" + secEndpoint;
                        }
                        secInfo.setUri(secEndpoint);
                        secondaryInstanceInfoList.add(secInfo);
                        log.info("Global Cluster secondary: id={}, endpoint={}", cluster.getClusterId(), secEndpoint);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取 Global Cluster topology 失败，仅使用 create_secondary 响应数据: {}", e.getMessage());
            // 退化：仅添加本次新增的 secondary
            for (String secId : newSecondaryIds) {
                InstanceInfo secInfo = new InstanceInfo();
                secInfo.setInstanceId(secId);
                secInfo.setUri(instanceUriMap.getOrDefault(secId, ""));
                secondaryInstanceInfoList.add(secInfo);
            }
        }

        Map<String, String> newSecondaryUris = buildUriMap(newSecondaryIds, instanceUriMap);

        log.info("Secondary 创建成功: globalClusterId={}, newSecondaryUris={}, globalEndpoint={}",
                globalClusterId, newSecondaryUris, globalEndpoint);

        return CreateSecondaryResult.builder()
                .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                .globalClusterId(globalClusterId)
                .primaryInstanceId(primaryInstanceInfo.getInstanceId())
                .primaryUri(primaryInstanceInfo.getUri())
                .newSecondaryInstanceIds(newSecondaryIds)
                .newSecondaryUris(newSecondaryUris)
                .globalEndpoint(globalEndpoint)
                .createCostSeconds(costSeconds).build();
    }

    private static Map<String, String> buildUriMap(List<String> instanceIds, Map<String, String> instanceUriMap) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String id : instanceIds) {
            result.put(id, instanceUriMap.getOrDefault(id, ""));
        }
        return result;
    }
}
