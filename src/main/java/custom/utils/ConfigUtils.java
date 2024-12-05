package custom.utils;

import com.alibaba.fastjson.JSONObject;
import custom.config.EnvConfig;
import custom.config.EnvEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigUtils {
    public static EnvConfig providerEnvConfig(EnvEnum envEnum){
        EnvConfig envConfig=new EnvConfig();
        String s = DatasetUtil.providerConfigFile(envEnum.vdcConfigPath);
        JSONObject configJO=JSONObject.parseObject(s);
        JSONObject envJO = configJO.getJSONObject("ENV");
        String rmHost = envJO.getJSONObject(envEnum.envNodeName).getString("rm_host");
        String cloudServiceHost = envJO.getJSONObject(envEnum.envNodeName).getString("cloud_service_host");
        String cloudServiceTestHost = envJO.getJSONObject(envEnum.envNodeName).getString("cloud_service_test_host");
        String regionId = envJO.getJSONObject(envEnum.envNodeName).getString("region_id");
        envConfig.setRegionId(regionId);
        envConfig.setCloudServiceHost(cloudServiceHost);
        envConfig.setRmHost(rmHost);
        envConfig.setCloudServiceTestHost(cloudServiceTestHost);
        // 文件里暂时未添加，临时方案
        if (envEnum==EnvEnum.AWS_WEST||envEnum==EnvEnum.GCP_WEST||envEnum==EnvEnum.AZURE_WEST){
            envConfig.setCloudOpsServiceHost("https://cloud-ops.cloud-uat3.zilliz.com");
        }
        if (envEnum==EnvEnum.ALI_HZ||envEnum==EnvEnum.TC_NJ){
            envConfig.setCloudOpsServiceHost("https://cloud-ops.cloud-uat.zilliz.cn");
        }
        return envConfig;
    }
}
