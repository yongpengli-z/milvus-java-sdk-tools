package custom.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static custom.BaseTest.envConfig;

@Slf4j
public class InfraServiceUtils {

    public static String getMilvusPodLabels(String cluster,String instanceId){
        String url=envConfig.getInfraHost()+"/api/v1/cloud/cluster/"+cluster+"/milvus/"+instanceId+"/v2/pod-labels";
        Map<String,String> header=new HashMap<>();
        header.put("authorization",envConfig.getInfraToken());
        return HttpClientUtils.doGet(url,header,null);
    }
}
