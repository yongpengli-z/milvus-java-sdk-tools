package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import custom.entity.CreateInstanceParams;
import custom.entity.DeleteInstanceParams;
import custom.entity.ModifyParams;
import custom.entity.RollingUpgradeParams;
import custom.pojo.ParamInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.*;

@Slf4j
public class ResourceManagerServiceUtils {
    public static String createInstance(CreateInstanceParams createInstanceParams) {
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/create";
        String body = "{\n" +
                "  \"classId\": \"" + createInstanceParams.getCuType() + "\",\n" +
                "  \"dbVersion\": \"" + createInstanceParams.getDbVersion() + "\",\n" +
                "  \"defaultCharacterset\": \"UTF-8\",\n" +
                "  \"defaultTimeZone\": \"UTC\",\n" +
                "  \"instanceDescription\": \"create by java tools\",\n" +
                "  \"instanceName\": \"" + createInstanceParams.getInstanceName() + "\",\n" +
                "  \"instanceType\": " + createInstanceParams.getInstanceType() + ",\n" +
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
        header.put("OrgId",  cloudServiceUserInfo.getOrgIdList().get(0));
        header.put("UserId", cloudServiceUserInfo.getOrgIdList().get(0));
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
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/delete";
        String instanceId = deleteInstanceParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : deleteInstanceParams.getInstanceId();
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
}

