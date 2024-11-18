package custom.utils;

import custom.entity.DeleteInstanceParams;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.envConfig;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class CloudServiceTestUtils {
    public static String deleteInstance(DeleteInstanceParams deleteInstanceParams){
        String instanceId = deleteInstanceParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : deleteInstanceParams.getInstanceId();
        String url = envConfig.getCloudServiceTestHost() + "/cloud/v1/test/deleteInstance?instanceId="+instanceId+"";
        return HttpClientUtils.doGet(url);
    }
}
