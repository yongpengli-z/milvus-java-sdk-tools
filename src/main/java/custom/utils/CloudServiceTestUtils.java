package custom.utils;

import custom.entity.DeleteInstanceParams;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.envEnum;
import static custom.BaseTest.envConfig;
import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class CloudServiceTestUtils {
    public static String deleteInstance(DeleteInstanceParams deleteInstanceParams){
        String inputInstanceId = deleteInstanceParams.getInstanceId();
        String instanceId = inputInstanceId == null || inputInstanceId.equalsIgnoreCase("")
                ? newInstanceInfo.getInstanceId()
                : inputInstanceId;
        return deleteInstanceById(instanceId);
    }

    public static String deleteInstanceById(String instanceId) {
        String url = envConfig.getCloudServiceTestHost() + "/cloud/v1/test/deleteInstance?instanceId="+instanceId+"";
        return HttpClientUtils.doGet(url);
    }

    public static String deleteSecondaryInstance(String instanceId, int globalClusterRole) {
        String url = envConfig.getCloudServiceTestHost()
                + "/cloud/v1/test/deleteSecondaryInstance?cloudId=" + resolveCloudId()
                + "&userId=" + resolveUserId()
                + "&instanceId=" + instanceId
                + "&globalClusterRole=" + globalClusterRole;
        String resp = HttpClientUtils.doGet(url);
        log.info("[cloud-test][deleteSecondaryInstance] cloudId={}, userId={}, instanceId={}, globalClusterRole={}, resp={}",
                resolveCloudId(), resolveUserId(), instanceId, globalClusterRole, resp);
        return resp;
    }

    public static String deleteInstanceNotInMeta(String instanceId) {
        String cloudId = resolveCloudId();
        String url = envConfig.getCloudServiceTestHost()
                + "/cloud/v1/test/deleteInstanceNotInMeta?cloudId=" + cloudId
                + "&instanceId=" + instanceId;
        String resp = HttpClientUtils.doGet(url);
        log.info("[cloud-test][deleteInstanceNotInMeta] cloudId={}, instanceId={}, resp={}", cloudId, instanceId, resp);
        return resp;
    }

    private static String resolveCloudId() {
        String region = envEnum == null ? "" : envEnum.region;
        if (region == null || region.isEmpty()) {
            region = envConfig.getRegionId();
        }
        if (region == null) {
            return "aws";
        }
        String lower = region.toLowerCase();
        if (lower.contains("gcp")) {
            return "gcp";
        }
        if (lower.contains("ali")) {
            return "ali";
        }
        if (lower.contains("tc")) {
            return "tc";
        }
        if (lower.contains("hwc")) {
            return "hwc";
        }
        if (lower.contains("az")) {
            return "az";
        }
        return "aws";
    }

    private static String resolveUserId() {
        String proxyUserId = cloudServiceUserInfo == null ? null : cloudServiceUserInfo.getProxyUserId();
        if (proxyUserId != null && !proxyUserId.isEmpty()) {
            return proxyUserId;
        }
        String userId = cloudServiceUserInfo == null ? null : cloudServiceUserInfo.getUserId();
        return userId == null ? "" : userId;
    }
}
