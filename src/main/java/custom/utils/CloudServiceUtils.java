package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.config.CloudServiceUserInfo;
import custom.pojo.InstanceInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.envConfig;

@Slf4j
public class CloudServiceUtils {
    public static CloudServiceUserInfo queryUserIdOfCloudService(String userName, String password){
        // 先登录vdc，获取token
        String loginUrl=envConfig.getCloudServiceHost()+"/account/v1/account/login";
        if (userName==null||userName.equalsIgnoreCase("")){
            userName="vdc_default_test@linshiyouxiang.net";
            password = "LyXp9%Hnxhl";
        }
        String jsonParam="{\"Email\":\""+userName+"\",\"Password\":\""+password+"\"}";
        Map<String,String> header=new HashMap<>();
        header.put("recaptcha-challenge-response","[]");
        String loginResp = HttpClientUtils.doPostJson(loginUrl,header,jsonParam);
        log.info("loginResp:"+loginResp);
        String token= JSON.parseObject(loginResp).getJSONObject("Data").getString("Token");
        String userId= JSON.parseObject(loginResp).getJSONObject("Data").getJSONObject("AccountInfo").getString("UserId");
        String accountName= JSON.parseObject(loginResp).getJSONObject("Data").getJSONObject("AccountInfo").getString("AccountName");
        CloudServiceUserInfo csUserInfo=new CloudServiceUserInfo();
        csUserInfo.setUserId(userId);
        csUserInfo.setToken(token);
        csUserInfo.setAccountName(accountName);
        // 获取orgid
        List<String> strings = listOrg(token);
        csUserInfo.setOrgIdList(strings);
        // 获取default project
        String defaultProject = providerDefaultProject(token, strings.get(0));
        csUserInfo.setDefaultProjectId(defaultProject);
        return csUserInfo;
    }


    public static List<InstanceInfo> listInstance(){
        String url=envConfig.getCloudServiceHost()+"/cloud/v1/instance/list?CurrentPage=1&PageSize=100&ProjectId="+cloudServiceUserInfo.getDefaultProjectId();
        Map<String,String> header=new HashMap<>();
        header.put("authorization","Bearer "+cloudServiceUserInfo.getToken());
        header.put("orgid",cloudServiceUserInfo.getOrgIdList().get(0));
        String s = HttpClientUtils.doGet(url, header,null);
//        log.info("[cloudService][listInstance]:"+s);
        List<InstanceInfo> instanceInfoList=new ArrayList<>();
        JSONArray jsonArray = JSON.parseObject(s).getJSONObject("Data").getJSONArray("List");
        for (int i = 0; i < jsonArray.size(); i++) {
            InstanceInfo instanceInfo=new InstanceInfo();
            instanceInfo.setInstanceId(jsonArray.getJSONObject(i).getString("InstanceId"));
            instanceInfo.setInstanceName(jsonArray.getJSONObject(i).getString("InstanceName"));
            instanceInfo.setUri(jsonArray.getJSONObject(i).getString("ConnectAddress"));
            instanceInfoList.add(instanceInfo);
        }
        return instanceInfoList;
    }

    public static String providerDefaultProject(String token,String orgId){
        String url = envConfig.getCloudServiceHost()+"/cloud/v1/project/list";
        Map<String,String> header=new HashMap<>();
        header.put("authorization","Bearer "+token);
        header.put("orgid",orgId);
        String s = HttpClientUtils.doGet(url, header,null);
        log.info("[cloudService][listProject]:"+s);
        JSONArray jsonArray = JSONObject.parseObject(s).getJSONObject("Data").getJSONArray("Projects");
        String dfProject="";
        for (int i = 0; i < jsonArray.size(); i++) {
            Boolean defaultProject =
                    jsonArray.getJSONObject(i).getBoolean("DefaultProject");
            if(defaultProject){
                dfProject=jsonArray.getJSONObject(i).getString("ProjectId");
                break;
            }
        }
        return dfProject;
    }

    public static List<String> listOrg(String token){
        String url=envConfig.getCloudServiceHost()+"/cloud/v1/org/list";
        Map<String,String> header=new HashMap<>();
        header.put("authorization","Bearer "+token);
        String s = HttpClientUtils.doGet(url, header,null);
        log.info("[cloudService][listOrg]:"+s);
        List<String> orgIdList=new ArrayList<>();
        JSONArray jsonArray = JSONObject.parseObject(s).getJSONObject("Data").getJSONArray("orgs");
        for (int i = 0; i < jsonArray.size(); i++) {
            String orgId = jsonArray.getJSONObject(i).getString("orgId");
            orgIdList.add(orgId);
        }
        return orgIdList;
    }
}
