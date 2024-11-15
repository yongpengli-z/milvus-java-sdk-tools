package custom.utils;

import custom.entity.CreateInstanceParams;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.envConfig;

@Slf4j
public class ResourceManagerServiceUtils {
    public static String createInstance(CreateInstanceParams createInstanceParams){
        String url = envConfig.getRmHost() + "/resource/v1/instance/milvus/create";
        String body="{\n" +
                "  \"classId\": \""+createInstanceParams.getCuType()+"\",\n" +
                "  \"dbVersion\": \""+createInstanceParams.getDbVersion()+"\",\n" +
                "  \"defaultCharacterset\": \"UTF-8\",\n" +
                "  \"defaultTimeZone\": \"UTC\",\n" +
                "  \"instanceDescription\": \"create by java tools\",\n" +
                "  \"instanceName\": \""+createInstanceParams.getInstanceName()+"\",\n" +
                "  \"instanceType\": "+createInstanceParams.getInstanceType()+",\n" +
                "  \"mockTag\": false,\n" +
                "  \"orgType\": \"SAAS\",\n" +
                "  \"processorArchitecture\": \""+createInstanceParams.getArchitecture()+"\",\n" +
                "  \"projectId\": \""+cloudServiceUserInfo.getDefaultProjectId()+"\",\n" +
                "  \"realUserId\": \""+cloudServiceUserInfo.getUserId()+"\",\n" +
                "  \"regionId\": \""+envConfig.getRegionId()+"\",\n" +
                "  \"replica\": "+createInstanceParams.getReplica()+",\n" +
                "  \"rootPwd\": \""+createInstanceParams.getRootPassword()+"\",\n" +
                "  \"trialExpireTimeMilli\": 1655184578000,\n" +
                "  \"vpcId\": \"\",\n" +
                "  \"whitelistAddress\": \"0.0.0.0/0\"\n" +
                "}";
        Map<String,String> header=new HashMap<>();
        header.put("RequestId","qtp-java-tools");
        header.put("UserId",cloudServiceUserInfo.getUserId());
        header.put("SourceApp","Cloud-Meta");
        String resp = HttpClientUtils.doPostJson(url,header,body);
        log.info("[rm-service][create instance]: "+ resp);
        return resp;
    }
}
