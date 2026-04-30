package custom.utils;

import custom.entity.DeleteInstanceParams;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.envEnum;
import static custom.BaseTest.envConfig;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class CloudServiceTestUtils {
    public static String deleteInstance(DeleteInstanceParams deleteInstanceParams){
        String instanceId = deleteInstanceParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : deleteInstanceParams.getInstanceId();
        String url = envConfig.getCloudServiceTestHost() + "/cloud/v1/test/deleteInstance?instanceId="+instanceId+"";
        return HttpClientUtils.doGet(url);
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
}
