package custom.utils;

import com.alibaba.fastjson.JSONObject;
import custom.config.EnvConfig;
import custom.config.EnvEnum;
import custom.exception.CustomException;
import custom.exception.CustomExceptionCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class ConfigUtils {
    public static EnvConfig providerEnvConfig(EnvEnum envEnum){
        if (envEnum == null) {
            throw new CustomException(CustomExceptionCode.INVALID_PARAMS, "Unknown env, cannot load VDC config");
        }
        EnvConfig envConfig=new EnvConfig();
        // 支持通过 -Dvdc.config.path 指定本地配置文件（便于本机调试），缺省用环境内置路径
        String vdcConfigPath = System.getProperty("vdc.config.path", envEnum.vdcConfigPath);
        if (vdcConfigPath == null || vdcConfigPath.trim().isEmpty()) {
            throw new CustomException(CustomExceptionCode.INVALID_PARAMS,
                    "VDC config path is empty for env: " + envEnum.region);
        }
        if (!new File(vdcConfigPath).isFile()) {
            throw new CustomException(CustomExceptionCode.RESOURCE_NOT_FOUND, "VDC config file does not exist for env "
                    + envEnum.region + ": " + vdcConfigPath);
        }
        String s = DatasetUtil.providerConfigFile(vdcConfigPath);
        JSONObject configJO=JSONObject.parseObject(s);
        if (configJO == null || configJO.getJSONObject("ENV") == null) {
            throw new CustomException(CustomExceptionCode.INVALID_RESPONSE,
                    "Invalid VDC config, missing ENV node: " + vdcConfigPath);
        }
        JSONObject envJO = configJO.getJSONObject("ENV");
        if (envJO.getJSONObject(envEnum.envNodeName) == null) {
            throw new CustomException(CustomExceptionCode.INVALID_RESPONSE, "Invalid VDC config, missing env node "
                    + envEnum.envNodeName + " in " + vdcConfigPath);
        }
        String rmHost = envJO.getJSONObject(envEnum.envNodeName).getString("rm_host");
        String cloudServiceHost = envJO.getJSONObject(envEnum.envNodeName).getString("cloud_service_host");
        String cloudServiceTestHost = envJO.getJSONObject(envEnum.envNodeName).getString("cloud_service_test_host");
        String regionId = envJO.getJSONObject(envEnum.envNodeName).getString("region_id");
        String cloudOpsServiceHost = envJO.getJSONObject(envEnum.envNodeName).getString("cloud_ops_service_host");
        String cloudOpsServiceToken = envJO.getJSONObject(envEnum.envNodeName).getString("cloud_ops_service_token");
        String cloudUserServiceToken = envJO.getJSONObject(envEnum.envNodeName).getString("cloud_user_service_host");
        String infraHost = envJO.getJSONObject(envEnum.envNodeName).getString("infra_host");
        String infraToken = envJO.getJSONObject(envEnum.envNodeName).getString("infra_token");
        envConfig.setRegionId(regionId);
        envConfig.setCloudServiceHost(cloudServiceHost);
        envConfig.setRmHost(rmHost);
        envConfig.setCloudServiceTestHost(cloudServiceTestHost);
        envConfig.setCloudOpsServiceHost(cloudOpsServiceHost);
        envConfig.setCloudOpsServiceToken(cloudOpsServiceToken);
        envConfig.setInfraHost(infraHost);
        envConfig.setInfraToken(infraToken);
        envConfig.setCloudUserServiceHost(cloudUserServiceToken);
        return envConfig;
    }
}
