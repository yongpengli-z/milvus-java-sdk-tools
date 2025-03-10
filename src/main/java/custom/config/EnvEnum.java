package custom.config;

import java.util.Arrays;

public enum EnvEnum {
    DEVOPS("devops", "/test/fouram/config/vdc_config.json", "",""),
    FOURAM("fouram", "/test/fouram/config/vdc_config.json", "",""),
    AWS_WEST("awswest", "/test/qtp/config/vdcConfig.json", "UAT3","uat-milvus-us-west-2"),
    GCP_WEST("gcpwest", "/test/qtp/config/vdcConfig.json", "UAT3-GCP","gcp-vdc-dev-test"),
    AZURE_WEST("azurewest", "/test/qtp/config/vdcConfig.json", "UAT3-AZURE","uat-az-milvus-westus3-1"),
    ALI_HZ("alihz", "/test/qtp/config/vdcConfig.json", "UAT",""),
    TC_NJ("tcnj", "/test/qtp/config/vdcConfig.json", "UAT","");

    public final String region;
    public final String vdcConfigPath;
    public final String envNodeName;

    public final String cluster;

    EnvEnum(String region, String vdcConfigPath, String envNodeName,String cluster) {
        this.region = region;
        this.vdcConfigPath = vdcConfigPath;
        this.envNodeName = envNodeName;
        this.cluster = cluster;
    }

    public static EnvEnum getEnvByName(String name) {
        return Arrays.stream(EnvEnum.values()).filter(x -> x.region.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
