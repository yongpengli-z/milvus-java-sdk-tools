package custom.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static custom.BaseTest.envConfig;

@Slf4j
public class CloudUserServiceUtils {
    public static String getProxyUserId(String orgId) {
        String url = envConfig.getCloudUserServiceHost() + "/user/v1/org/find";
        Map<String, String> header = new HashMap<>();
        header.put("RequestId", "qtp-java-tools-" + MathUtil.genRandomString(10));
        Map<String, String> params = new HashMap<>();
        params.put("orgId", orgId);
        String s = HttpClientUtils.doGet(url, header, params);
        log.info("[CloudUserService]request head:" + header);
        log.info("[CloudUserService]request params:" + params);
        log.info("[CloudUserService]response:" + s);
        return s;
    }
}
