package custom.utils;

import com.alibaba.fastjson.JSONObject;
import custom.entity.CreateInstanceParams;
import custom.entity.DeleteInstanceParams;
import custom.entity.ModifyParams;
import custom.entity.RollingUpgradeParams;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
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
        header.put("RequestId", "qtp-java-tools");
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        String resp = HttpClientUtils.doPostJson(url, header, body);
        log.info("[rm-service][create instance]: " + resp);
        return resp;
    }

    public static String describeInstance(String instanceId) {
        if (instanceId==null || instanceId.equals("")) {
            instanceId = newInstanceInfo.getInstanceId();
        }
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/describe?InstanceId=" + instanceId;
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools");
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
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
        header.put("RequestId", "qtp-java-tools");
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
        header.put("RequestId", "qtp-java-tools");
        header.put("UserId", cloudServiceUserInfo.getUserId());
        header.put("SourceApp", "Cloud-Meta");
        return HttpClientUtils.doPostJson(url, header, JSONObject.parseObject(body).toJSONString());
    }

    public static String modifyParams(ModifyParams modifyParams){
        String url = envConfig.getRmHost() +"/resource/v1/param/milvus/modify";
        String instanceId = modifyParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : modifyParams.getInstanceId();
        String body = "";
        return null;

    }
}

