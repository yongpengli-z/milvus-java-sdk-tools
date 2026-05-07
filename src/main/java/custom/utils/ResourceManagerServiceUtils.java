package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import custom.entity.CreateGlobalClusterParams;
import custom.entity.CreateInstanceParams;
import custom.entity.CreateSecondaryParams;
import custom.entity.DeleteInstanceParams;
import custom.entity.ModifyParams;
import custom.entity.RollingUpgradeParams;
import custom.pojo.ParamInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.*;

@Slf4j
public class ResourceManagerServiceUtils {
    public static String createInstance(CreateInstanceParams createInstanceParams) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/create";
        String kmsField = "";
        if (createInstanceParams.getKmsIntegrationId() != null && !createInstanceParams.getKmsIntegrationId().isEmpty()) {
            kmsField = "  \"enableCMEK\": true,\n" +
                    "  \"kmsIntegrationId\": \"" + createInstanceParams.getKmsIntegrationId() + "\",\n";
        }
        String body = "{\n" +
                "  \"classId\": \"" + createInstanceParams.getCuType() + "\",\n" +
                "  \"dbVersion\": \"" + createInstanceParams.getDbVersion() + "\",\n" +
                "  \"defaultCharacterset\": \"UTF-8\",\n" +
                "  \"defaultTimeZone\": \"UTC\",\n" +
                "  \"instanceDescription\": \"create by java tools\",\n" +
                "  \"instanceName\": \"" + createInstanceParams.getInstanceName() + "\",\n" +
                "  \"instanceType\": " + createInstanceParams.getInstanceType() + ",\n" +
                kmsField +
                "  \"mockTag\": false,\n" +
                "  \"orgType\": \"SAAS\",\n" +
                "  \"processorArchitecture\": \"" + createInstanceParams.getArchitecture() + "\",\n" +
                "  \"projectId\": \"" + cloudServiceUserInfo.getDefaultProjectId() + "\",\n" +
                "  \"realUserId\": \"" + cloudServiceUserInfo.getUserId() + "\",\n" +
                "  \"regionId\": \"" + envConfig.getRegionId() + "\",\n" +
                "  \"replica\": " + createInstanceParams.getReplica() + ",\n" +
                "  \"rootPwd\": \"" + createInstanceParams.getRootPassword() + "\",\n" +
                "  \"trialExpireTimeMilli\": 1655184578000,\n" +
                "  \"vpcId\": \"\",\n" +
                "  \"whitelistAddress\": \"0.0.0.0/0\"\n" +
                "}";
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
//        header.put("OrgId",  cloudServiceUserInfo.getOrgIdList().get(0));
        header.put("UserId", cloudServiceUserInfo.getProxyUserId());
        header.put("SourceApp", "Cloud-Meta");
        String resp = HttpClientUtils.doPostJson(url, header, body);
        log.info("[head cloudServiceUserInfo]: " + cloudServiceUserInfo);
        log.info("[rm-service][create instance]: " + resp);
        return resp;
    }

    public static String describeInstance(String instanceId) {
        if (instanceId == null || instanceId.equals("")) {
            instanceId = newInstanceInfo.getInstanceId();
        }
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/describe?innerCall=true&InstanceId=" + instanceId;
        Map<String, String> header = new HashMap<>();
        String requestId = "qtp-java-tools-" + MathUtil.genRandomString(10);
        header.put("RequestId", requestId);
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        log.info("head-requestId: " + requestId);
        return HttpClientUtils.doGet(url, header, null);
    }

    public static String rollingUpgrade(RollingUpgradeParams rollingUpgradeParams) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/rolling/upgrade/add/task";
        String body = "{\n" +
                "  \"actionTimestamp\": " + System.currentTimeMillis() + ",\n" +
                "  \"force\": true,\n" +
                "  \"forceRestart\": " + rollingUpgradeParams.isForceRestart() + ",\n" +
                "  \"instanceId\": \"" + newInstanceInfo.getInstanceId() + "\",\n" +
                "  \"needBackup\": false,\n" +
                "  \"notChangeStatus\": false,\n" +
                "  \"notCheckStatus\": false,\n" +
                "  \"syncDeploymentConfig\": true,\n" +
                "  \"syncHookConfig\": true,\n" +
                "  \"syncMilvusConfig\": true,\n" +
                "  \"targetDbVersion\": \"" + rollingUpgradeParams.getTargetDbVersion() + "\"\n" +
                "}";
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        return HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(body).toJSONString());
    }

    public static String deleteInstance(DeleteInstanceParams deleteInstanceParams) {
        String instanceId = deleteInstanceParams.getInstanceId() == null || deleteInstanceParams.getInstanceId().equalsIgnoreCase("")
                ? newInstanceInfo.getInstanceId()
                : deleteInstanceParams.getInstanceId();
        return deleteInstanceById(instanceId);
    }

    public static String deleteInstanceById(String instanceId) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/delete";
        String body = "{\n" +
                "  \"backupId\": \"\",\n" +
                "  \"force\": true,\n" +
                "  \"instanceId\": \"" + instanceId + "\",\n" +
                "  \"storageDelay\": 0\n" +
                "}";
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        return HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(body).toJSONString());
    }

    /**
     * RM 层删除 Global Cluster secondary。
     * <p>
     * 对应接口 POST /resource/v1/global_cluster/milvus/delete_secondary。
     */
    public static String deleteGlobalSecondaryInstance(String instanceId, String globalClusterId) {
        String url = envConfig.getRmHost() + "/resource/v1/global_cluster/milvus/delete_secondary";
        Map<String, Object> body = new HashMap<>();
        body.put("instanceId", instanceId);
        if (globalClusterId != null && !globalClusterId.isEmpty()) {
            body.put("globalClusterId", globalClusterId);
        }
        body.put("backupId", "");
        body.put("force", true);
        body.put("storageDelay", "0");
        body.put("enableChildJobCenter", true);
        String resp = postToRmAsProxyUser(url, new Gson().toJson(body), "delete global secondary");
        log.info("[rm-service][delete global secondary] instanceId={}, globalClusterId={}, resp={}",
                instanceId, globalClusterId, resp);
        return resp;
    }

    /**
     * RM 层 disband Global Cluster，把 primary 转回普通实例。
     * <p>
     * 对应接口 POST /resource/v1/global_cluster/milvus/disband。
     */
    public static String disbandGlobalCluster(String globalClusterId, String primaryInstanceId) {
        String url = envConfig.getRmHost() + "/resource/v1/global_cluster/milvus/disband";
        Map<String, Object> body = new HashMap<>();
        body.put("globalClusterId", globalClusterId);
        body.put("instanceId", primaryInstanceId);
        String resp = postToRmAsProxyUser(url, new Gson().toJson(body), "disband global cluster");
        log.info("[rm-service][disband global cluster] globalClusterId={}, primaryInstanceId={}, resp={}",
                globalClusterId, primaryInstanceId, resp);
        return resp;
    }

    public static void modifyParams(String instanceId, List<ModifyParams.Params> paramsList) {
        String url = envConfig.getRmHost() + "/resource/v1/param/milvus/modify";
        String instanceIdTemp = (instanceId == null || instanceId.equalsIgnoreCase("")) ? newInstanceInfo.getInstanceId() : instanceId;
        for (ModifyParams.Params params : paramsList) {
            String body = "{\n" +
                    "\n" +
                    "  \"force\": true,\n" +
                    "  \"instanceId\": \"" + instanceIdTemp + "\",\n" +
                    "  \"paramName\": \"" + params.getParamName() + "\",\n" +
                    "  \"paramValue\": \"" + params.getParamValue() + "\",\n" +
                    "  \"switchType\": 0\n" +
                    "}";
            Map<String, String> header = new HashMap<>();
            header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
            header.put("UserId", cloudServiceUserInfo.getUserId());
            header.put("SourceApp", "Cloud-Meta");
            String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(body).toJSONString());
            log.info("Modify [" + params + "] response:" + s);
        }
    }

    public static List<ParamInfo> listParams(String instanceId) {
        String instanceIdTemp = (instanceId == null || instanceId.equalsIgnoreCase("")) ? newInstanceInfo.getInstanceId() : instanceId;
        String url = envConfig.getRmHost() + "/resource/v1/param/milvus/list?all=true&InstanceId=" + instanceIdTemp;
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String resp = HttpClientUtils.doGet(url, header, null);
        List<ParamInfo> paramInfoList = new ArrayList<>();
        JSONObject respJO = JSON.parseObject(resp);
        Integer code = respJO.getInteger("Code");
        if (code == 0) {
            JSONArray jsonArray = respJO.getJSONObject("Data").getJSONArray("list");
            for (int i = 0; i < jsonArray.size(); i++) {
                ParamInfo paramInfo = new ParamInfo();
                JSONObject item = jsonArray.getJSONObject(i);
                paramInfo.setParamName(item.getString("paramName"));
                paramInfo.setCurrentValue(item.getString("currentValue"));
                paramInfo.setFinalValue(item.getString("finalValue"));
                paramInfoList.add(paramInfo);
            }
        }
        return paramInfoList;
    }

    public static void addParams(String instanceId, List<ModifyParams.Params> paramsList) {
        String url = envConfig.getRmHost() + "/resource/v1/param/milvus/add";
        String instanceIdTemp = (instanceId == null || instanceId.equalsIgnoreCase("")) ? newInstanceInfo.getInstanceId() : instanceId;
        for (ModifyParams.Params params : paramsList) {
            String body = "{\n" +
                    "  \"backendModify\": true,\n" +
                    "  \"force\": true,\n" +
                    "  \"instanceId\": \"" + instanceIdTemp + "\",\n" +
                    "  \"paramName\": \"" + params.getParamName() + "\",\n" +
                    "  \"paramPropertyAlter\": true,\n" +
                    "  \"paramPropertyDisplay\": false,\n" +
                    "  \"paramPropertyRestart\": false,\n" +
                    "  \"paramValue\": \"" + params.getParamValue() + "\"\n" +
                    "}";
            Map<String, String> header = new HashMap<>();
            header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
            header.put("UserId", cloudServiceUserInfo.getUserId());
            header.put("SourceApp", "Cloud-Meta");
            String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(body).toJSONString());
            log.info("Add [" + params + "] response:" + s);
        }
    }

    public static String restartInstance(String instanceId) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/restart";
        String instanceIdTemp = (instanceId == null || instanceId.equalsIgnoreCase("")) ? newInstanceInfo.getInstanceId() : instanceId;
        String body = "{\n" +
                "  \"force\": true,\n" +
                "  \"instanceId\": \"" + instanceIdTemp + "\",\n" +
                "  \"notChangeStatus\": false,\n" +
                "  \"switchType\": 0\n" +
                "}";
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(body).toJSONString());
        log.info("Restart instance:" + s);
        return s;
    }

    public static String updateLabel(String instanceId) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_biz_critical?InstanceId=" + instanceId;
        Gson gson = new Gson();
        Map<String, Object> params = new HashMap<>();
        params.put("enable", true);
        params.put("nodeCategories", Arrays.asList("queryNode", "standalone"));
        String jsonParams = gson.toJson(params);
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(jsonParams).toJSONString());
        log.info("update label:" + s);
        return s;
    }

    public static String updateQNMonopoly(String instanceId) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_qn_monopoly?InstanceId=" + instanceId;
        Gson gson = new Gson();
        Map<String, Object> params = new HashMap<>();
        params.put("enable", true);
        String jsonParams = gson.toJson(params);
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(jsonParams).toJSONString());
        log.info("update qn monopoly: " + s);
        return s;
    }

    public static String updateQNBreakUp(String instanceId) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_qn_break_up?InstanceId=" + instanceId;
        Gson gson = new Gson();
        Map<String, Object> params = new HashMap<>();
        params.put("enable", true);
        String jsonParams = gson.toJson(params);
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(jsonParams).toJSONString());
        log.info("update qn break up: " + s);
        return s;
    }

    public static String updateReplica(String instanceId, int replicaNum, List<String> nodeCategories) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_replicas?InstanceId=" + instanceId;
        Gson gson = new Gson();
        Map<String, Object> params = new HashMap<>();
        params.put("replicas", replicaNum);
        params.put("nodeCategories", nodeCategories);
        String jsonParams = gson.toJson(params);
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(jsonParams).toJSONString());
        log.info("update " + nodeCategories + " replica: " + s);
        return s;
    }

    public static String modifyInstance(String instanceId, String classId, int replica) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/modify";
        Gson gson = new Gson();
        Map<String, Object> params = new HashMap<>();
        params.put("instanceId", instanceId);
        params.put("classId", classId);
        if (replica > 0) {
            params.put("replica", replica);
        }
        String jsonParams = gson.toJson(params);
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(jsonParams).toJSONString());
        log.info("modify instance: " + s);
        return s;
    }

    public static String updateLimits(String instanceId, String cpu, String memory, String disk, List<String> nodeCategories) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_limits?InstanceId=" + instanceId;
        Gson gson = new Gson();
        Map<String, Object> params = new HashMap<>();
        params.put("nodeCategories", nodeCategories);
        Map<String, Object> resourceListParam = new HashMap<>();
        resourceListParam.put("cpu", cpu);
        resourceListParam.put("memory", memory);
        resourceListParam.put("ephemeral-storage", disk);
        params.put("resourceList", resourceListParam);
        String jsonParams = gson.toJson(params);
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String s = HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(jsonParams).toJSONString());
        log.info("update " + nodeCategories + " limits: " + s);
        return s;

    }

    /**
     * 根据基础 classId 和 replica 数构造最终 classId。
     * 规则：replica=1 时省略，replica>=2 时插在 CU 数后面。
     * 例：class-8-enterprise + replica=2 -> class-8-2-enterprise
     *     class-8-disk-enterprise + replica=3 -> class-8-3-disk-enterprise
     */
    public static String buildClassId(String baseClassId, int replica) {
        String normalized = stripReplica(baseClassId);
        if (replica <= 1) {
            return normalized;
        }
        int firstDash = normalized.indexOf("-");
        int secondDash = normalized.indexOf("-", firstDash + 1);
        return normalized.substring(0, secondDash) + "-" + replica + normalized.substring(secondDash);
    }

    /**
     * 去掉 classId 中的 replica 部分，还原为 1-replica 形式。
     * class-8-2-enterprise -> class-8-enterprise
     * class-8-3-disk-enterprise -> class-8-disk-enterprise
     */
    public static String stripReplica(String classId) {
        String[] parts = classId.split("-");
        if (parts.length >= 4 && parts[2].matches("\\d+")) {
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
     * 从 classId 中提取 CU 数。
     * class-8-enterprise -> 8, class-128-2-disk-enterprise -> 128
     */
    public static int extractCu(String classId) {
        String[] parts = classId.split("-");
        return Integer.parseInt(parts[1]);
    }

    /**
     * RM 通用 POST 帮助方法：拼 header，发 body，返回原始响应。
     * 复用 UpdateInstanceComponentComp 等新组件，避免每个方法各自组装 header。
     */
    private static String postToRm(String url, String body) {
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        return HttpClientUtils.doPostJson(url, header, body);
    }

    /**
     * Global cluster 创建类接口需要 UserId 使用实例归属的 proxy user，RealUserId 保留真实登录用户。
     */
    private static String postToRmAsProxyUser(String url, String body, String action) {
        Map<String, String> header = new HashMap<>();
        String requestId = "qtp-java-tools-" + MathUtil.genRandomString(10);
        String realUserId = cloudServiceUserInfo.getUserId();
        String proxyUserId = cloudServiceUserInfo.getProxyUserId();
        String orgId = cloudServiceUserInfo.getOrgIdList() == null || cloudServiceUserInfo.getOrgIdList().isEmpty()
                ? ""
                : cloudServiceUserInfo.getOrgIdList().get(0);
        if (proxyUserId == null || proxyUserId.isEmpty()) {
            proxyUserId = realUserId;
        }
        header.put("RequestId", requestId);
        header.put("UserId", proxyUserId);
        header.put("RealUserId", realUserId);
        header.put("SourceApp", "Cloud-Meta");
        if (!orgId.isEmpty()) {
            header.put("OrgId", orgId);
        }
        log.info("[rm-service][{} request user info]\n- requestId={}\n- UserId(proxyUserId)={}\n- RealUserId={}\n- OrgId={}\n- ProjectId={}",
                action, requestId, proxyUserId, realUserId, orgId, cloudServiceUserInfo.getDefaultProjectId());
        return HttpClientUtils.doPostJson(url, header, body);
    }

    /**
     * 修改指定 NodeCategory 的 pod 副本数。
     * <p>
     * 对应 RM 接口 POST /resource/v1/instance/milvus/update_replicas?InstanceId=xxx。
     * NodeCategories 是广播语义 —— 列表里所有 category 会被设置成同一个 replicas 值。
     * 如果要让不同 category 用不同 replicas，应拆成多次调用。
     *
     * @param instanceId     实例 ID
     * @param replicas       目标 pod 副本数
     * @param nodeCategories NodeCategory specName 列表，例 ["queryNode"]、["dataNode","indexNode"]
     * @param replicaIndex   多副本组场景的组下标；null 表示作用于所有副本组
     * @return RM 原始响应
     */
    public static String updateReplicas(String instanceId, int replicas,
                                        List<String> nodeCategories, Integer replicaIndex) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_replicas?InstanceId=" + instanceId;
        Map<String, Object> params = new HashMap<>();
        params.put("replicas", replicas);
        params.put("nodeCategories", nodeCategories);
        if (replicaIndex != null) {
            params.put("replicaIndex", replicaIndex);
        }
        String body = new Gson().toJson(params);
        String resp = postToRm(url, body);
        log.info("update_replicas [{} replicaIndex={}] replicas={} resp={}", nodeCategories, replicaIndex, replicas, resp);
        return resp;
    }

    /**
     * 修改指定 NodeCategory 的 pod resource requests。
     * <p>
     * 对应 RM 接口 POST /resource/v1/instance/milvus/update_requests?InstanceId=xxx。
     * resourceList 里的 value 必须是 k8s Quantity 字符串（例 "8000m"、"32Gi"），原样透传。
     * 注意 RM 会整组覆盖 ResourceList，调用方需自行保证 cpu/memory 一起传，
     * 否则未传的维度可能被清空。
     *
     * @param instanceId     实例 ID
     * @param resourceList   resource map，例 {"cpu":"8000m","memory":"32Gi"}
     * @param nodeCategories NodeCategory specName 列表
     * @param replicaIndex   多副本组场景的组下标；null 表示作用于所有副本组
     * @return RM 原始响应
     */
    public static String updateRequests(String instanceId, Map<String, String> resourceList,
                                        List<String> nodeCategories, Integer replicaIndex) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_requests?InstanceId=" + instanceId;
        Map<String, Object> params = new HashMap<>();
        params.put("resourceList", resourceList);
        params.put("nodeCategories", nodeCategories);
        if (replicaIndex != null) {
            params.put("replicaIndex", replicaIndex);
        }
        String body = new Gson().toJson(params);
        String resp = postToRm(url, body);
        log.info("update_requests [{} replicaIndex={}] resourceList={} resp={}", nodeCategories, replicaIndex, resourceList, resp);
        return resp;
    }

    /**
     * 修改指定 NodeCategory 的 pod resource limits。
     * <p>
     * 对应 RM 接口 POST /resource/v1/instance/milvus/update_limits?InstanceId=xxx。
     * 注意事项同 {@link #updateRequests}。
     *
     * @param instanceId     实例 ID
     * @param resourceList   resource map，例 {"cpu":"8000m","memory":"32Gi"}
     * @param nodeCategories NodeCategory specName 列表
     * @param replicaIndex   多副本组场景的组下标；null 表示作用于所有副本组
     * @return RM 原始响应
     */
    public static String updateLimits(String instanceId, Map<String, String> resourceList,
                                      List<String> nodeCategories, Integer replicaIndex) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/update_limits?InstanceId=" + instanceId;
        Map<String, Object> params = new HashMap<>();
        params.put("resourceList", resourceList);
        params.put("nodeCategories", nodeCategories);
        if (replicaIndex != null) {
            params.put("replicaIndex", replicaIndex);
        }
        String body = new Gson().toJson(params);
        String resp = postToRm(url, body);
        log.info("update_limits [{} replicaIndex={}] resourceList={} resp={}", nodeCategories, replicaIndex, resourceList, resp);
        return resp;
    }

    /**
     * 创建 Global Cluster（主实例 + secondary 实例列表）。
     * <p>
     * 对应 RM 接口 POST /resource/v1/global_cluster/milvus/create。
     */
    public static String createGlobalCluster(CreateGlobalClusterParams params) {
        String url = envConfig.getRmHost() + "/resource/v1/global_cluster/milvus/create";
        Gson gson = new Gson();
        Map<String, Object> body = new HashMap<>();
        body.put("regionId", params.getRegionId() != null ? params.getRegionId() : envConfig.getRegionId());
        body.put("realUserId", cloudServiceUserInfo.getUserId());
        body.put("dbVersion", params.getDbVersion());
        body.put("classId", params.getClassId());
        body.put("replica", params.getReplica());
        body.put("instanceName", params.getInstanceName());
        body.put("instanceType", params.getInstanceType());
        body.put("instanceDescription", params.getInstanceDescription());
        body.put("projectId", cloudServiceUserInfo.getDefaultProjectId());
        body.put("orgType", params.getOrgType());
        body.put("processorArchitecture", params.getArchitecture());
        body.put("defaultTimeZone", "UTC");
        body.put("defaultCharacterset", "UTF-8");
        body.put("whitelistAddress", "0.0.0.0/0");
        if (params.getRootPwd() != null && !params.getRootPwd().isEmpty()) {
            body.put("rootPwd", params.getRootPwd());
        }
        if (params.getSecondaryClusters() != null && !params.getSecondaryClusters().isEmpty()) {
            List<Map<String, Object>> secondaries = new ArrayList<>();
            for (CreateGlobalClusterParams.SecondaryCluster sc : params.getSecondaryClusters()) {
                Map<String, Object> scMap = new HashMap<>();
                scMap.put("regionId", sc.getRegionId());
                scMap.put("instanceName", sc.getInstanceName());
                scMap.put("classId", sc.getClassId());
                if (sc.getReplica() != null) {
                    scMap.put("replica", sc.getReplica());
                }
                secondaries.add(scMap);
            }
            body.put("secondaryClusters", secondaries);
        }
        body.put("enableChildJobCenter", params.isEnableChildJobCenter());
        String jsonBody = gson.toJson(body);
        String resp = postToRmAsProxyUser(url, jsonBody, "create global cluster");
        log.info("[rm-service][create global cluster]: {}", resp);
        return resp;
    }

    /**
     * 为已有实例添加 Secondary 集群（也可将普通实例转为 Global Cluster）。
     * <p>
     * 对应 RM 接口 POST /resource/v1/global_cluster/milvus/create_secondary。
     * <ul>
     *   <li>场景 A：传 instanceId（普通实例）→ 自动转为 Global Cluster 并添加 secondary</li>
     *   <li>场景 B：传 globalClusterId（已有 GC）→ 在已有 GC 下添加新 secondary</li>
     * </ul>
     */
    public static String createSecondary(CreateSecondaryParams params) {
        String url = envConfig.getRmHost() + "/resource/v1/global_cluster/milvus/create_secondary";
        Gson gson = new Gson();
        Map<String, Object> body = new HashMap<>();
        // 场景 A：普通实例转 GC
        if (params.getInstanceId() != null && !params.getInstanceId().isEmpty()) {
            body.put("instanceId", params.getInstanceId());
        }
        // 场景 B：已有 GC 扩展 secondary
        if (params.getGlobalClusterId() != null && !params.getGlobalClusterId().isEmpty()) {
            body.put("globalClusterId", params.getGlobalClusterId());
        }
        body.put("realUserId", cloudServiceUserInfo.getUserId());
        body.put("projectId", cloudServiceUserInfo.getDefaultProjectId());
        body.put("instanceType", 1); // InstanceType.MILVUS
        if (params.getSecondaryClusters() != null && !params.getSecondaryClusters().isEmpty()) {
            List<Map<String, Object>> secondaries = new ArrayList<>();
            for (CreateSecondaryParams.SecondaryCluster sc : params.getSecondaryClusters()) {
                Map<String, Object> scMap = new HashMap<>();
                scMap.put("regionId", sc.getRegionId());
                if (sc.getInstanceName() != null && !sc.getInstanceName().isEmpty()) {
                    scMap.put("instanceName", sc.getInstanceName());
                }
                if (sc.getClassId() != null && !sc.getClassId().isEmpty()) {
                    scMap.put("classId", sc.getClassId());
                }
                if (sc.getReplica() != null) {
                    scMap.put("replica", sc.getReplica());
                }
                secondaries.add(scMap);
            }
            body.put("secondaryClusters", secondaries);
        }
        body.put("enableChildJobCenter", params.isEnableChildJobCenter());
        String jsonBody = gson.toJson(body);
        String resp = postToRmAsProxyUser(url, jsonBody, "create secondary");
        log.info("[rm-service][create secondary]: {}", resp);
        return resp;
    }

    /**
     * 从 describeInstance 响应中提取 GlobalClusterId。
     *
     * @param instanceId 实例 ID
     * @return GlobalClusterId，不属于 Global Cluster 时返回 null
     */
    public static String getGlobalClusterId(String instanceId) {
        String resp = describeInstance(instanceId);
        JSONObject jo = JSONObject.parseObject(resp);
        Integer code = getResponseCode(jo);
        JSONObject data = getResponseData(jo);
        if (isSuccessCode(code) && data != null) {
            String gcId = firstNonBlank(data.getString("GlobalClusterId"), data.getString("globalClusterId"));
            log.info("describeInstance 反查 GlobalClusterId: instanceId={}, globalClusterId={}", instanceId, gcId);
            if (gcId != null && !gcId.isEmpty()) {
                return gcId;
            }
        } else {
            log.warn("describeInstance 反查 GlobalClusterId 失败: instanceId={}, code={}, message={}",
                    instanceId, code, getResponseMessage(jo));
        }
        return null;
    }

    /**
     * 通过 cloud-service 查询 Global Cluster 的 ConnectAddress（global endpoint）。
     *
     * @param globalClusterId Global Cluster ID（gdc-xxx）
     * @return global endpoint（如 https://gdc-xxx.global-cluster.xxx.zilliz.com:19530），查询失败返回 null
     */
    public static String describeGlobalClusterEndpoint(String globalClusterId) {
        String resp = describeGlobalCluster(globalClusterId);
        String connectAddress = parseGlobalEndpoint(resp, "ConnectAddress", "connectAddress");
        if (connectAddress != null && !connectAddress.isEmpty()) {
            log.info("describe global cluster endpoint success from cloud-service: globalClusterId={}, endpoint={}", globalClusterId, connectAddress);
            return normalizeHttps(connectAddress);
        }

        log.warn("cloud-service describe global cluster 未返回 endpoint，尝试 cloud-ops fallback: globalClusterId={}", globalClusterId);
        String opsEndpoint = describeGlobalClusterEndpointFromOps(globalClusterId);
        if (opsEndpoint != null && !opsEndpoint.isEmpty()) {
            return normalizeHttps(opsEndpoint);
        }
        return null;
    }

    public static String describeGlobalCluster(String globalClusterId) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/global_cluster/describe?globalClusterId=" + globalClusterId;
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("Authorization", "Bearer " + cloudServiceUserInfo.getToken());
        if (cloudServiceUserInfo.getOrgIdList() != null && !cloudServiceUserInfo.getOrgIdList().isEmpty()) {
            header.put("orgid", cloudServiceUserInfo.getOrgIdList().get(0));
        }
        String resp = HttpClientUtils.doGet(url, header, null);
        log.info("[cloud-service][describe global cluster]: {}", resp);
        return resp;
    }

    public static String describeGlobalClusterTopology(String globalClusterId) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/global_cluster/describe_topology?globalClusterId=" + globalClusterId;
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("Authorization", "Bearer " + cloudServiceUserInfo.getToken());
        if (cloudServiceUserInfo.getOrgIdList() != null && !cloudServiceUserInfo.getOrgIdList().isEmpty()) {
            header.put("orgid", cloudServiceUserInfo.getOrgIdList().get(0));
        }
        String resp = HttpClientUtils.doGet(url, header, null);
        log.info("[cloud-service][describe global cluster topology]: {}", resp);
        return resp;
    }

    private static String describeGlobalClusterEndpointFromOps(String globalClusterId) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/ops/resource/custInstance/globalCluster/" + globalClusterId;
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        String resp = HttpClientUtils.doGet(url, header, null);
        log.info("[cloud-ops][describe global cluster]: {}", resp);
        String connectAddress = parseGlobalEndpoint(resp, "globalEndpoint", "GlobalEndpoint", "ConnectAddress", "connectAddress");
        if (connectAddress != null && !connectAddress.isEmpty()) {
            log.info("describe global cluster endpoint success from cloud-ops: globalClusterId={}, endpoint={}", globalClusterId, connectAddress);
            return connectAddress;
        }
        log.warn("cloud-ops describe global cluster endpoint 为空: globalClusterId={}", globalClusterId);
        return null;
    }

    private static String parseGlobalEndpoint(String resp, String... endpointFields) {
        if (resp == null || resp.isEmpty()) {
            return null;
        }
        JSONObject jo = JSONObject.parseObject(resp);
        Integer code = getResponseCode(jo);
        JSONObject data = getResponseData(jo);
        if (isSuccessCode(code) && data != null) {
            for (String field : endpointFields) {
                String endpoint = data.getString(field);
                if (endpoint != null && !endpoint.isEmpty()) {
                    return endpoint;
                }
            }
        } else {
            log.warn("describe global cluster response failed: code={}, message={}", code, getResponseMessage(jo));
        }
        return null;
    }

    private static String normalizeHttps(String endpoint) {
        if (endpoint == null || endpoint.isEmpty() || endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
            return endpoint;
        }
        return "https://" + endpoint;
    }

    private static Integer getResponseCode(JSONObject jo) {
        Integer code = jo.getInteger("Code");
        return code != null ? code : jo.getInteger("code");
    }

    private static String getResponseMessage(JSONObject jo) {
        String message = jo.getString("Message");
        return message != null ? message : jo.getString("message");
    }

    private static JSONObject getResponseData(JSONObject jo) {
        JSONObject data = jo.getJSONObject("Data");
        return data != null ? data : jo.getJSONObject("data");
    }

    private static boolean isSuccessCode(Integer code) {
        return code != null && (code == 0 || code == 200);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
