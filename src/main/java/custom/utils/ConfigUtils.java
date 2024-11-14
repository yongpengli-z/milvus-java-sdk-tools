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
        String regionId = envJO.getJSONObject(envEnum.envNodeName).getString("region_id");
        envConfig.setRegionId(regionId);
        envConfig.setCloudServiceHost(cloudServiceHost);
        envConfig.setRmHost(rmHost);
        return envConfig;
    }
}
