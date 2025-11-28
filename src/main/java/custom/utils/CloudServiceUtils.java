package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.config.CloudServiceUserInfo;
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
    public static CloudServiceUserInfo queryUserIdOfCloudService(String userName, String password) {
        // 先登录vdc，获取token
        String loginUrl = envConfig.getCloudServiceHost().replace("cloud-service", "cloud-account") + "/account/inner/v1/account/login";
        if (userName == null || userName.equalsIgnoreCase("")) {
            userName = "vdc_default_test@linshiyouxiang.net";
            password = "LyXp9%Hnxhl";
        }
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
        return csUserInfo;
    }


    public static List<InstanceInfo> listInstance() {
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/instance/list?CurrentPage=1&PageSize=100&ProjectId=" + cloudServiceUserInfo.getDefaultProjectId();
        Map<String, String> header = new HashMap<>();
        header.put("authorization", "Bearer " + cloudServiceUserInfo.getToken());
        header.put("orgid", cloudServiceUserInfo.getOrgIdList().get(0));
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
        Map<String, String> header = new HashMap<>();
        header.put("authorization", "Bearer " + cloudServiceUserInfo.getToken());
        header.put("orgid", cloudServiceUserInfo.getOrgIdList().get(0));
        String s = HttpClientUtils.doPostJson(url, header, body);
        log.info("[Cloud-Service]stop-instance:" + s);
        return s;
    }

    public static String resumeInstance(ResumeInstanceParams resumeInstanceParams) {
        String instanceId = resumeInstanceParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : resumeInstanceParams.getInstanceId();
        String url = envConfig.getCloudServiceHost() + "/cloud/v1/instance/resume";
        String body = "{\"instanceId\":\"" + instanceId + "\"}";
        Map<String, String> header = new HashMap<>();
        header.put("authorization", "Bearer " + cloudServiceUserInfo.getToken());
        header.put("orgid", cloudServiceUserInfo.getOrgIdList().get(0));
        String s = HttpClientUtils.doPostJson(url, header, body);
        log.info("[Cloud-Service]resume-instance:" + s);
        return s;
    }


}
