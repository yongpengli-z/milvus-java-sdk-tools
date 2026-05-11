package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.config.CloudServiceUserInfo;
import custom.entity.CreateGlobalClusterParams;
import custom.entity.CreateQueryClusterParams;
import custom.entity.CreateSecondaryParams;
import custom.entity.ResumeInstanceParams;
import custom.entity.StopInstanceParams;
import custom.pojo.InstanceInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.*;

@Slf4j
public class CloudServiceUtils {
    private static Map<String, String> buildCloudServiceHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("authorization", "Bearer " + cloudServiceUserInfo.getToken());
        header.put("orgid", cloudServiceUserInfo.getOrgIdList().get(0));
        header.put("UserId", cloudServiceUserInfo.getProxyUserId());
        header.put("RealUserId", cloudServiceUserInfo.getUserId());
        header.put("sourceApp", "Cloud-Meta");
        header.put("requestId", "milvus-java-tools-" + System.currentTimeMillis());
        return header;
    }

    public static CloudServiceUserInfo queryUserIdOfCloudService(String userName, String password) {
        // 先登录vdc，获取token
        String loginUrl = envConfig.getCloudServiceHost().replace("cloud-service", "cloud-account") + "/account/inner/v1/account/login";
        boolean usingDefaultAccount = userName == null || userName.equalsIgnoreCase("");
        if (usingDefaultAccount) {
            userName = "vdc_default_test@linshiyouxiang.net";
            password = "LyXp9%Hnxhl";
        }
        log.info("[cloudService][login] accountEmail={}, usingDefaultAccount={}", userName, usingDefaultAccount);
        String jsonParam = "{\"Email\":\"" + userName + "\",\"Password\":\"" + password + "\"}";
        Map<String, String> header = new HashMap<>();
        header.put("recaptcha-challenge-response", "[]");
        String loginResp = HttpClientUtils.doPostJson(loginUrl, header, jsonParam);
        log.info("loginResp:" + loginResp);
        String token = JSON.parseObject(loginResp).getJSONObject("Data").getString("Token");
        String userId = JSON.parseObject(loginResp).getJSONObject("Data").getJSONObject("AccountInfo").getString("UserId");
        String accountName = JSON.parseObject(loginResp).getJSONObject("Data").getJSONObject("AccountInfo").getString("AccountName");
        CloudServiceUserInfo csUserInfo = new CloudServiceUserInfo();
        csUserInfo.setUserId(userId);
        csUserInfo.setToken(token);
        csUserInfo.setAccountName(accountName);
        // 获取orgid
        List<String> strings = listOrg(token);
        csUserInfo.setOrgIdList(strings);
        // 获取proxyUserId
        String respCUS = CloudUserServiceUtils.getProxyUserId(strings.get(0));
        int code = JSON.parseObject(respCUS).getInteger("Code");
        if (code == 0) {
            String proxyUserId = JSON.parseObject(respCUS).getJSONObject("Data").getString("proxyUserId");
            csUserInfo.setProxyUserId(proxyUserId);
            log.info("查询到proxyUserId：" + proxyUserId);
        } else {
            csUserInfo.setProxyUserId(userId);
            log.info("查询cloudUserService失败，使用userid(" + userId + ")替代proxyUserId");
        }
        // 获取default project
        String defaultProject = providerDefaultProject(token, strings.get(0));
        csUserInfo.setDefaultProjectId(defaultProject);
        log.info("[cloudService][login user info]\n- accountEmail={}\n- accountName={}\n- userId={}\n- proxyUserId={}\n- orgId={}\n- defaultProjectId={}",
                userName, accountName, userId, csUserInfo.getProxyUserId(), strings.get(0), defaultProject);
        return csUserInfo;
    }


    public static List<InstanceInfo> listInstance() {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/instance/list?CurrentPage=1&PageSize=100&ProjectId=" + cloudServiceUserInfo.getDefaultProjectId();
        Map<String, String> header = buildCloudServiceHeader();
        log.info("authorization token: " + "Bearer " + cloudServiceUserInfo.getToken());
        log.info("使用的 orgid: " + cloudServiceUserInfo.getOrgIdList().get(0));
        String s = HttpClientUtils.doGet(url, header, null);
        log.info("[cloudService][listInstance]:" + s);
        List<InstanceInfo> instanceInfoList = new ArrayList<>();
        JSONArray jsonArray = JSON.parseObject(s).getJSONObject("Data").getJSONArray("List");
        for (int i = 0; i < jsonArray.size(); i++) {
            InstanceInfo instanceInfo = new InstanceInfo();
            instanceInfo.setInstanceId(jsonArray.getJSONObject(i).getString("InstanceId"));
            instanceInfo.setInstanceName(jsonArray.getJSONObject(i).getString("InstanceName"));
            instanceInfo.setUri(jsonArray.getJSONObject(i).getString("ConnectAddress"));
            instanceInfoList.add(instanceInfo);
        }
        log.info("[Cloud-service]List instance:" + instanceInfoList);
        return instanceInfoList;
    }

    public static String providerDefaultProject(String token, String orgId) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/project/list";
        Map<String, String> header = new HashMap<>();
        header.put("authorization", "Bearer " + token);
        header.put("orgid", orgId);
        String s = HttpClientUtils.doGet(url, header, null);
        log.info("[cloudService][listProject]:" + s);
        JSONArray jsonArray = JSONObject.parseObject(s).getJSONObject("Data").getJSONArray("Projects");
        String dfProject = "";
        for (int i = 0; i < jsonArray.size(); i++) {
            Boolean defaultProject =
                    jsonArray.getJSONObject(i).getBoolean("DefaultProject");
            if (defaultProject) {
                dfProject = jsonArray.getJSONObject(i).getString("ProjectId");
                break;
            }
        }
        return dfProject;
    }

    public static String resolveProjectId(String projectId, String projectName) {
        if (projectId != null && !projectId.isEmpty()) {
            return projectId;
        }
        if (projectName == null || projectName.isEmpty()) {
            return cloudServiceUserInfo.getDefaultProjectId();
        }
        JSONArray projects = listProjects();
        if (projects == null) {
            return null;
        }
        for (int i = 0; i < projects.size(); i++) {
            JSONObject project = projects.getJSONObject(i);
            if (projectName.equalsIgnoreCase(project.getString("ProjectName"))) {
                return project.getString("ProjectId");
            }
        }
        return null;
    }

    public static JSONArray listProjects() {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/project/list";
        String s = HttpClientUtils.doGet(url, buildCloudServiceHeader(), null);
        log.info("[cloudService][listProject]:" + s);
        JSONObject data = JSONObject.parseObject(s).getJSONObject("Data");
        if (data == null || data.getJSONArray("Projects") == null) {
            return new JSONArray();
        }
        return data.getJSONArray("Projects");
    }

    public static String describeVectorLake(String projectId, String regionId) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/vectorlake?projectId=" + projectId
                + "&regionId=" + regionId;
        String resp = HttpClientUtils.doGet(url, buildCloudServiceHeader(), null);
        log.info("[cloud-service][describe vectorlake] projectId={}, regionId={}, resp={}",
                projectId, regionId, resp);
        return resp;
    }

    public static String createVectorLake(String projectId, String regionId, String sessionTTL,
            Integer maxQueryNodeCU, Integer maxQueryNodeReplicas) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/vectorlake";
        JSONObject body = new JSONObject();
        body.put("projectId", projectId);
        body.put("regionId", regionId);
        body.put("sessionTTL", sessionTTL == null || sessionTTL.isEmpty() ? "30m" : sessionTTL);
        if (maxQueryNodeCU != null) {
            body.put("maxQueryNodeCU", maxQueryNodeCU);
        }
        if (maxQueryNodeReplicas != null) {
            body.put("maxQueryNodeReplicas", maxQueryNodeReplicas);
        }
        String resp = HttpClientUtils.doPostJson(url, buildCloudServiceHeader(), body.toJSONString());
        log.info("[cloud-service][create vectorlake] body={}, resp={}", body.toJSONString(), resp);
        return resp;
    }

    public static String createQueryCluster(CreateQueryClusterParams params, String projectId, String regionId) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/vectorlake/query-cluster";
        JSONObject body = new JSONObject();
        body.put("projectId", projectId);
        body.put("regionId", regionId);
        body.put("clusterName", params.getClusterName());
        body.put("cuSize", params.getCuSize());
        body.put("sessionTTL", params.getSessionTTL() == null || params.getSessionTTL().isEmpty()
                ? "30m" : params.getSessionTTL());
        if (params.getMaxQueryNodeCU() != null) {
            body.put("maxQueryNodeCU", params.getMaxQueryNodeCU());
        }
        if (params.getMaxQueryNodeReplicas() != null) {
            body.put("maxQueryNodeReplicas", params.getMaxQueryNodeReplicas());
        }
        String resp = HttpClientUtils.doPostJson(url, buildCloudServiceHeader(), body.toJSONString());
        log.info("[cloud-service][create query cluster] body={}, resp={}", body.toJSONString(), resp);
        return resp;
    }

    public static String getQueryCluster(String projectId, String regionId, String clusterId) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/vectorlake/query-cluster/" + clusterId
                + "?projectId=" + projectId + "&regionId=" + regionId;
        String resp = HttpClientUtils.doGet(url, buildCloudServiceHeader(), null);
        log.info("[cloud-service][get query cluster] projectId={}, regionId={}, clusterId={}, resp={}",
                projectId, regionId, clusterId, resp);
        return resp;
    }

    public static String listQueryClusters(String projectId, String regionId) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/vectorlake/query-cluster?projectId=" + projectId;
        if (regionId != null && !regionId.isEmpty()) {
            url += "&regionId=" + regionId;
        }
        String resp = HttpClientUtils.doGet(url, buildCloudServiceHeader(), null);
        log.info("[cloud-service][list query cluster] projectId={}, regionId={}, resp={}",
                projectId, regionId, resp);
        return resp;
    }

    public static String listManagedApiKeys() {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/apikey/list-managed-key";
        String resp = HttpClientUtils.doGet(url, buildCloudServiceHeader(), null);
        log.info("[cloud-service][list managed api keys] resp={}", maskApiKeyResponse(resp));
        return resp;
    }

    private static String maskApiKeyResponse(String response) {
        try {
            JSONObject root = JSONObject.parseObject(response);
            JSONObject data = root.getJSONObject("Data");
            if (data == null) {
                return response;
            }
            JSONArray keys = data.getJSONArray("keys");
            if (keys == null) {
                keys = data.getJSONArray("Keys");
            }
            if (keys == null) {
                return response;
            }
            for (int i = 0; i < keys.size(); i++) {
                JSONObject key = keys.getJSONObject(i);
                if (key.containsKey("key")) {
                    key.put("key", "***");
                }
                if (key.containsKey("Key")) {
                    key.put("Key", "***");
                }
            }
            return root.toJSONString();
        } catch (Exception e) {
            return response;
        }
    }

    public static List<String> listOrg(String token) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/org/list";
        Map<String, String> header = new HashMap<>();
        header.put("authorization", "Bearer " + token);
        String s = HttpClientUtils.doGet(url, header, null);
        log.info("[cloudService][listOrg]:" + s);
        List<String> orgIdList = new ArrayList<>();
        JSONArray jsonArray = JSONObject.parseObject(s).getJSONObject("Data").getJSONArray("orgs");
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObjectItem = jsonArray.getJSONObject(i);
            if (jsonObjectItem.getString("type").equalsIgnoreCase("SAAS") && !jsonObjectItem.getBoolean("frozen")) {
                String orgId = jsonObjectItem.getString("orgId");
                orgIdList.add(orgId);
            }
        }
        return orgIdList;
    }

    public static String stopInstance(StopInstanceParams stopInstanceParams) {
        String instanceId = stopInstanceParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : stopInstanceParams.getInstanceId();
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/instance/stop";
        String body = "{\"instanceId\":\"" + instanceId + "\"}";
        Map<String, String> header = buildCloudServiceHeader();
        String s = HttpClientUtils.doPostJson(url, header, body);
        log.info("[Cloud-Service]stop-instance:" + s);
        return s;
    }

    public static String resumeInstance(ResumeInstanceParams resumeInstanceParams) {
        String instanceId = resumeInstanceParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : resumeInstanceParams.getInstanceId();
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/instance/resume";
        String body = "{\"instanceId\":\"" + instanceId + "\"}";
        Map<String, String> header = buildCloudServiceHeader();
        String s = HttpClientUtils.doPostJson(url, header, body);
        log.info("[Cloud-Service]resume-instance:" + s);
        return s;
    }

    /**
     * 通过 cloud-service 创建 Global Cluster，保留 RM 创建方法用于兼容和对比。
     */
    public static String createGlobalCluster(CreateGlobalClusterParams params) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/global_cluster/create";
        JSONObject body = new JSONObject();
        body.put("classId", params.getClassId());
        body.put("replica", params.getReplica());
        body.put("instanceDescription", params.getInstanceDescription());
        body.put("instanceName", params.getInstanceName());
        body.put("regionId", params.getRegionId() != null ? params.getRegionId() : envConfig.getRegionId());
        body.put("projectId", cloudServiceUserInfo.getDefaultProjectId());
        body.put("dbVersion", params.getDbVersion());
        body.put("globalClusterName", params.getInstanceName());
        if (params.getRootPwd() != null && !params.getRootPwd().isEmpty()) {
            body.put("rootPwd", params.getRootPwd());
        }

        JSONArray secondaryClusters = new JSONArray();
        if (params.getSecondaryClusters() != null) {
            for (CreateGlobalClusterParams.SecondaryCluster secondary : params.getSecondaryClusters()) {
                JSONObject secondaryBody = new JSONObject();
                secondaryBody.put("regionId", secondary.getRegionId());
                secondaryBody.put("instanceName", secondary.getInstanceName());
                secondaryClusters.add(secondaryBody);
            }
        }
        body.put("secondaryClusters", secondaryClusters);

        String resp = HttpClientUtils.doPostJson(url, buildCloudServiceHeader(), body.toJSONString());
        log.info("[cloud-service][create global cluster] body={}, resp={}", body.toJSONString(), resp);
        return resp;
    }

    /**
     * 通过 cloud-service 添加 Secondary，cloud-service 会根据 primary/meta 推导 secondary 规格。
     */
    public static String createSecondary(CreateSecondaryParams params) {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/global_cluster/create_secondary";
        JSONObject body = new JSONObject();
        body.put("projectId", cloudServiceUserInfo.getDefaultProjectId());
        if (params.getInstanceId() != null && !params.getInstanceId().isEmpty()) {
            body.put("instanceId", params.getInstanceId());
        }
        if (params.getGlobalClusterId() != null && !params.getGlobalClusterId().isEmpty()) {
            body.put("globalClusterId", params.getGlobalClusterId());
            body.put("globalClusterName", params.getGlobalClusterId());
        }

        JSONArray secondaryClusters = new JSONArray();
        if (params.getSecondaryClusters() != null) {
            for (CreateSecondaryParams.SecondaryCluster secondary : params.getSecondaryClusters()) {
                JSONObject secondaryBody = new JSONObject();
                secondaryBody.put("regionId", secondary.getRegionId());
                if (secondary.getInstanceName() != null && !secondary.getInstanceName().isEmpty()) {
                    secondaryBody.put("instanceName", secondary.getInstanceName());
                }
                secondaryClusters.add(secondaryBody);
            }
        }
        body.put("secondaryClusters", secondaryClusters);

        String resp = HttpClientUtils.doPostJson(url, buildCloudServiceHeader(), body.toJSONString());
        log.info("[cloud-service][create secondary] body={}, resp={}", body.toJSONString(), resp);
        return resp;
    }

}
