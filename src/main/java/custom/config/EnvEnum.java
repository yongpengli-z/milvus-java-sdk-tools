package custom.config;

import java.util.Arrays;

public enum EnvEnum {
    DEVOPS("devops", "/test/fouram/config/vdc_config.json", ""),
    FOURAM("fouram", "/test/fouram/config/vdc_config.json", ""),
    AWS_WEST("awswest", "/test/qtp/config/vdcConfig.json", "UAT3"),
    GCP_WEST("gcpwest", "/test/qtp/config/vdcConfig.json", "UAT3-GCP"),
    AZURE_WEST("azurewest", "/test/qtp/config/vdcConfig.json", "UAT3-AZURE"),
    ALI_HZ("alihz", "/test/fouram/config/vdc_config.json", "UAT"),
    TC_NJ("tcnj", "/test/fouram/config/vdc_config.json", "UAT");

    public final String region;
    public final String vdcConfigPath;
    public final String envNodeName;

    EnvEnum(String region, String vdcConfigPath, String envNodeName) {
        this.region = region;
        this.vdcConfigPath = vdcConfigPath;
        this.envNodeName = envNodeName;
    }

    public static EnvEnum getEnvByName(String name) {
        return Arrays.stream(EnvEnum.values()).filter(x -> x.region.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
