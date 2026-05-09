package custom.components;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.common.ImageType;
import custom.common.InstanceStatusEnum;
import custom.entity.CreateQueryClusterParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateInstanceResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static custom.BaseTest.*;

@Slf4j
public class CreateQueryClusterComp {
    private static final int DEFAULT_WAIT_MINUTES = 30;
    private static final String IMPLICIT_VECTOR_LAKE_CREATE = "__implicit_vectorlake_create__";

    public static CreateInstanceResult createQueryCluster(CreateQueryClusterParams params) {
        LocalDateTime startTime = LocalDateTime.now();
        try {
            return doCreateQueryCluster(params, startTime);
        } catch (Exception e) {
            log.error("[CreateQueryCluster] failed", e);
            return fail("create query cluster exception: " + e.getMessage(), startTime, null);
        }
    }

    private static CreateInstanceResult doCreateQueryCluster(CreateQueryClusterParams params, LocalDateTime startTime) {
        ensureCloudServiceLogin(params);

        String regionId = params.getRegionId() == null || params.getRegionId().isEmpty()
                ? envConfig.getRegionId() : params.getRegionId();
        String projectId = CloudServiceUtils.resolveProjectId(params.getProjectId(), params.getProjectName());
        if (!hasText(projectId)) {
            return fail("projectId is required or projectName must match an existing project.", startTime, null);
        }
        if (params.getCuSize() < 8) {
            return fail("cuSize must be >= 8", startTime, null);
        }
        if (params.getClusterName() == null || params.getClusterName().isEmpty()) {
            return fail("clusterName is required", startTime, null);
        }
        if (queryClusterNameExists(projectId, regionId, params.getClusterName())) {
            return fail("The specified query cluster name already exists.", startTime, null);
        }

        String vectorLakeId = prepareVectorLake(params, projectId, regionId, startTime);
        if (vectorLakeId == null) {
            return fail("VectorLake is required before creating QueryCluster. Set autoCreateVectorLake=true to create it.",
                    startTime, null);
        }

        String createResp = CloudServiceUtils.createQueryCluster(params, projectId, regionId);
        JSONObject createJO = JSONObject.parseObject(createResp);
        if (!isApiSuccess(createJO)) {
            return fail("create query cluster failed: " + getMessage(createJO, createResp), startTime, null);
        }
        String instanceId = getString(getData(createJO), "instanceId", "InstanceId", "clusterId", "ClusterId");
        if (!hasText(instanceId)) {
            return fail("create query cluster failed: response does not contain instanceId", startTime, null);
        }

        ComponentSchedule.initInstanceStatus(instanceId, "", resolveDisplayVersion(params), InstanceStatusEnum.CREATING.code);
        JSONObject queryCluster = waitQueryClusterRunning(projectId, regionId, instanceId);
        if (queryCluster == null) {
            ComponentSchedule.updateInstanceStatus(instanceId, "--", resolveDisplayVersion(params),
                    InstanceStatusEnum.CREATE_FAILED.code);
            return fail("QueryCluster create timeout or failed.", startTime, instanceId);
        }

        if (params.isAutoUpgradeQueryCluster() && hasText(params.getQueryClusterDbVersion())) {
            String targetVersion = resolveQueryClusterDbVersion(params.getQueryClusterDbVersion());
            String upgradeResp = ResourceManagerServiceUtils.upgradeQueryCluster(instanceId, targetVersion,
                    params.isForceUpgradeQueryCluster());
            JSONObject upgradeJO = JSONObject.parseObject(upgradeResp);
            if (!isApiSuccess(upgradeJO)) {
                return fail("upgrade query cluster failed: " + getMessage(upgradeJO, upgradeResp), startTime, instanceId);
            }
            if (!waitQueryClusterUpgradeComplete(instanceId)) {
                return fail("QueryCluster upgrade timeout.", startTime, instanceId);
            }
        }

        String endpoint = getString(queryCluster, "endpoint", "Endpoint", "connectAddress", "ConnectAddress");
        newInstanceInfo.setInstanceId(instanceId);
        newInstanceInfo.setInstanceName(getString(queryCluster, "clusterName", "ClusterName"));
        if (newInstanceInfo.getInstanceName() == null || newInstanceInfo.getInstanceName().isEmpty()) {
            newInstanceInfo.setInstanceName(params.getClusterName());
        }
        newInstanceInfo.setUri(endpoint);

        String token = resolveToken(params);
        if (hasText(token)) {
            newInstanceInfo.setToken(token);
        }
        if (params.isConnectAfterCreate()) {
            if (!hasText(endpoint)) {
                return fail("QueryCluster endpoint is empty.", startTime, instanceId);
            }
            if (!hasText(newInstanceInfo.getToken())) {
                ComponentSchedule.updateInstanceStatus(instanceId, endpoint, resolveDisplayVersion(params),
                        InstanceStatusEnum.RUNNING.code);
                return warning(instanceId, endpoint, startTime,
                        "QueryCluster created, but no API key was available for client initialization.");
            }
            milvusClientV2 = MilvusConnect.createMilvusClientV2(endpoint, newInstanceInfo.getToken());
            milvusClientV1 = MilvusConnect.createMilvusClientV1(endpoint, newInstanceInfo.getToken());
        }

        ComponentSchedule.updateInstanceStatus(instanceId, endpoint, resolveDisplayVersion(params),
                InstanceStatusEnum.RUNNING.code);
        return CreateInstanceResult.builder()
                .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                .instanceId(instanceId)
                .uri(endpoint)
                .createCostSeconds((int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now()))
                .build();
    }

    private static void ensureCloudServiceLogin(CreateQueryClusterParams params) {
        if (cloudServiceUserInfo.getUserId() != null && !cloudServiceUserInfo.getUserId().isEmpty()) {
            return;
        }
        if (params.getAccountEmail() == null || params.getAccountEmail().isEmpty()) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
        } else {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(
                    params.getAccountEmail(), params.getAccountPassword());
        }
    }

    private static String prepareVectorLake(CreateQueryClusterParams params, String projectId, String regionId,
            LocalDateTime startTime) {
        JSONObject vectorLakeStatus = describeVectorLake(projectId, regionId);
        JSONObject vectorLakeData = getData(vectorLakeStatus);
        boolean exists = vectorLakeData != null && Boolean.TRUE.equals(getBoolean(vectorLakeData, "exists", "Exists"));
        if (!exists) {
            if (!params.isAutoCreateVectorLake()) {
                return null;
            }
            if (!hasText(params.getVectorLakeDbVersion())) {
                return IMPLICIT_VECTOR_LAKE_CREATE;
            }
            if (hasText(params.getVectorLakeDbVersion()) && !params.isAutoUpgradeVectorLake()) {
                return null;
            }
            String createResp = CloudServiceUtils.createVectorLake(projectId, regionId, params.getSessionTTL(),
                    params.getMaxQueryNodeCU(), params.getMaxQueryNodeReplicas());
            JSONObject createJO = JSONObject.parseObject(createResp);
            if (!isApiSuccess(createJO)) {
                throw new IllegalStateException("create vectorlake failed: " + getMessage(createJO, createResp));
            }
            String vectorLakeId = getString(getData(createJO), "instanceId", "InstanceId");
            if (!hasText(vectorLakeId)) {
                throw new IllegalStateException("create vectorlake failed: response does not contain instanceId");
            }
            ComponentSchedule.initInstanceStatus(vectorLakeId, "", params.getVectorLakeDbVersion(),
                    InstanceStatusEnum.CREATING.code);
            if (!waitInstanceRunning(vectorLakeId)) {
                ComponentSchedule.updateInstanceStatus(vectorLakeId, "--", params.getVectorLakeDbVersion(),
                        InstanceStatusEnum.CREATE_FAILED.code);
                throw new IllegalStateException("VectorLake create timeout.");
            }
            exists = true;
            vectorLakeData = getData(describeVectorLake(projectId, regionId));
        }
        if (!exists || vectorLakeData == null) {
            return null;
        }
        String vectorLakeId = getString(vectorLakeData, "instanceId", "InstanceId");
        String status = getString(vectorLakeData, "status", "Status");
        if (!"RUNNING".equalsIgnoreCase(status) && !waitInstanceRunning(vectorLakeId)) {
            throw new IllegalStateException("VectorLake is not RUNNING: " + status);
        }
        if (hasText(params.getVectorLakeDbVersion())) {
            ensureVectorLakeVersion(vectorLakeId, params.getVectorLakeDbVersion(),
                    params.isAutoUpgradeVectorLake(), startTime);
        }
        return vectorLakeId;
    }

    private static void ensureVectorLakeVersion(String vectorLakeId, String expectedVersion, boolean allowUpgrade,
            LocalDateTime startTime) {
        String targetVersion = resolveVectorLakeDbVersion(expectedVersion);
        if (!hasText(targetVersion)) {
            throw new IllegalStateException("vectorLakeDbVersion must be an exact ins_type=6 dbVersion.");
        }
        String describeResp = ResourceManagerServiceUtils.describeInstance(vectorLakeId);
        JSONObject data = getData(JSONObject.parseObject(describeResp));
        String currentVersion = getString(data, "DBVersion", "dbVersion");
        if (targetVersion.equals(currentVersion)) {
            return;
        }
        if (!allowUpgrade) {
            throw new IllegalStateException("VectorLake dbVersion mismatch. current=" + currentVersion
                    + ", expected=" + targetVersion + ". Set autoUpgradeVectorLake=true to upgrade in06.");
        }
        String upgradeResp = ResourceManagerServiceUtils.upgradeVectorLakeCoordinator(vectorLakeId, targetVersion);
        JSONObject upgradeJO = JSONObject.parseObject(upgradeResp);
        if (!isApiSuccess(upgradeJO)) {
            throw new IllegalStateException("upgrade vectorlake failed: " + getMessage(upgradeJO, upgradeResp));
        }
        if (!waitVectorLakeUpgradeComplete(vectorLakeId)) {
            throw new IllegalStateException("VectorLake upgrade timeout after "
                    + ChronoUnit.SECONDS.between(startTime, LocalDateTime.now()) + " seconds.");
        }
    }

    private static JSONObject describeVectorLake(String projectId, String regionId) {
        String resp = CloudServiceUtils.describeVectorLake(projectId, regionId);
        JSONObject jo = JSONObject.parseObject(resp);
        if (!isApiSuccess(jo)) {
            throw new IllegalStateException("describe vectorlake failed: " + getMessage(jo, resp));
        }
        return jo;
    }

    private static boolean queryClusterNameExists(String projectId, String regionId, String clusterName) {
        String resp = CloudServiceUtils.listQueryClusters(projectId, regionId);
        JSONObject jo = JSONObject.parseObject(resp);
        if (!isApiSuccess(jo)) {
            return false;
        }
        JSONArray clusters = jo.getJSONArray("Data");
        if (clusters == null) {
            clusters = jo.getJSONArray("data");
        }
        if (clusters == null) {
            return false;
        }
        for (int i = 0; i < clusters.size(); i++) {
            JSONObject cluster = clusters.getJSONObject(i);
            String name = getString(cluster, "clusterName", "ClusterName");
            if (clusterName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static JSONObject waitQueryClusterRunning(String projectId, String regionId, String instanceId) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(DEFAULT_WAIT_MINUTES);
        while (LocalDateTime.now().isBefore(endTime)) {
            String resp = CloudServiceUtils.getQueryCluster(projectId, regionId, instanceId);
            JSONObject jo = JSONObject.parseObject(resp);
            if (isApiSuccess(jo)) {
                JSONObject data = getData(jo);
                String status = getString(data, "status", "Status");
                log.info("[CreateQueryCluster] current status: {}", status);
                if ("RUNNING".equalsIgnoreCase(status)) {
                    return data;
                }
                if ("ABNORMAL".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) {
                    return null;
                }
            }
            sleepSeconds(30);
        }
        return null;
    }

    private static boolean waitInstanceRunning(String instanceId) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(DEFAULT_WAIT_MINUTES);
        while (LocalDateTime.now().isBefore(endTime)) {
            String resp = ResourceManagerServiceUtils.describeInstance(instanceId);
            JSONObject data = getData(JSONObject.parseObject(resp));
            Integer status = getInteger(data, "Status", "status");
            if (status != null) {
                log.info("[CreateQueryCluster] instance {} status: {}", instanceId,
                        InstanceStatusEnum.getInstanceStatusByCode(status));
                if (status == InstanceStatusEnum.RUNNING.code) {
                    return true;
                }
                if (status == InstanceStatusEnum.ABNORMAL.code || status == InstanceStatusEnum.DELETED.code) {
                    return false;
                }
            }
            sleepSeconds(30);
        }
        return false;
    }

    private static boolean waitVectorLakeUpgradeComplete(String instanceId) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(DEFAULT_WAIT_MINUTES);
        while (LocalDateTime.now().isBefore(endTime)) {
            String resp = ResourceManagerServiceUtils.getVectorLakeCoordinatorUpgradeStatus(instanceId);
            JSONObject data = getData(JSONObject.parseObject(resp));
            Boolean complete = getBoolean(data, "rolloutComplete", "RolloutComplete");
            if (Boolean.TRUE.equals(complete)) {
                return true;
            }
            sleepSeconds(10);
        }
        return false;
    }

    private static boolean waitQueryClusterUpgradeComplete(String instanceId) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(DEFAULT_WAIT_MINUTES);
        while (LocalDateTime.now().isBefore(endTime)) {
            String resp = ResourceManagerServiceUtils.getQueryClusterUpgradeStatus(instanceId);
            JSONObject data = getData(JSONObject.parseObject(resp));
            Boolean complete = getBoolean(data, "rolloutComplete", "RolloutComplete");
            if (Boolean.TRUE.equals(complete)) {
                return true;
            }
            sleepSeconds(10);
        }
        return false;
    }

    private static String resolveToken(CreateQueryClusterParams params) {
        if (hasText(params.getApiKey())) {
            return params.getApiKey();
        }
        if (!params.isUsePersonalApiKey()) {
            return "";
        }
        String resp = CloudServiceUtils.listManagedApiKeys();
        JSONObject data = getData(JSONObject.parseObject(resp));
        JSONArray keys = data == null ? null : data.getJSONArray("keys");
        if (keys == null && data != null) {
            keys = data.getJSONArray("Keys");
        }
        if (keys == null) {
            return "";
        }
        for (int i = 0; i < keys.size(); i++) {
            JSONObject key = keys.getJSONObject(i);
            Integer type = getInteger(key, "type", "Type");
            String apiKey = getString(key, "key", "Key");
            if (type != null && type == 1 && hasText(apiKey)) {
                return apiKey;
            }
        }
        return "";
    }

    private static String resolveDisplayVersion(CreateQueryClusterParams params) {
        if (hasText(params.getQueryClusterDbVersion())) {
            return params.getQueryClusterDbVersion();
        }
        return "QueryCluster";
    }

    private static String resolveVectorLakeDbVersion(String version) {
        if (!hasText(version)) {
            return version;
        }
        if (version.equalsIgnoreCase("latest-release")) {
            return null;
        }
        return resolveDbVersion(version, ImageType.VECTOR_LAKE.getInsType());
    }

    private static String resolveQueryClusterDbVersion(String version) {
        if (!hasText(version)) {
            return version;
        }
        if (version.equalsIgnoreCase("latest-release")) {
            return "";
        }
        return resolveDbVersion(version, ImageType.QUERY_CLUSTER.getInsType());
    }

    private static String resolveDbVersion(String version, int insType) {
        String latestImageByKeywords = CloudOpsServiceUtils.getLatestImageByKeywords(version, insType);
        if (latestImageByKeywords != null && latestImageByKeywords.contains("(")) {
            return latestImageByKeywords.substring(0, latestImageByKeywords.indexOf("("));
        }
        return version;
    }

    private static CreateInstanceResult fail(String message, LocalDateTime startTime, String instanceId) {
        log.warn(message);
        CreateInstanceResult result = CreateInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message(message)
                        .build())
                .createCostSeconds((int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now()))
                .build();
        if (hasText(instanceId)) {
            result.setInstanceId(instanceId);
        }
        return result;
    }

    private static CreateInstanceResult warning(String instanceId, String endpoint, LocalDateTime startTime,
            String message) {
        log.warn(message);
        return CreateInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.WARNING.result)
                        .message(message)
                        .build())
                .instanceId(instanceId)
                .uri(endpoint)
                .createCostSeconds((int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now()))
                .build();
    }

    private static boolean isApiSuccess(JSONObject jo) {
        Integer code = getInteger(jo, "Code", "code");
        return code != null && code == 0;
    }

    private static JSONObject getData(JSONObject jo) {
        if (jo == null) {
            return null;
        }
        JSONObject data = jo.getJSONObject("Data");
        return data == null ? jo.getJSONObject("data") : data;
    }

    private static String getString(JSONObject jo, String... keys) {
        if (jo == null) {
            return null;
        }
        for (String key : keys) {
            String value = jo.getString(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer getInteger(JSONObject jo, String... keys) {
        if (jo == null) {
            return null;
        }
        for (String key : keys) {
            Integer value = jo.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Boolean getBoolean(JSONObject jo, String... keys) {
        if (jo == null) {
            return null;
        }
        for (String key : keys) {
            Boolean value = jo.getBoolean(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String getMessage(JSONObject jo, String fallback) {
        String message = getString(jo, "Message", "message");
        return message == null ? fallback : message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage());
        }
    }
}
