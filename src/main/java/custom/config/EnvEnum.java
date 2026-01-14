package custom.config;

import java.util.Arrays;

public enum EnvEnum {
    DEVOPS("devops", "/test/fouram/config/vdc_config.json", "","","/root/.kube/config"),
    FOURAM("fouram", "/test/fouram/config/vdc_config.json", "","","/root/.kube/config"),
    AWS_WEST("awswest", "/test/qtp/config/vdcConfig.json", "UAT3","uat-milvus-us-west-2","/root/.kube/config"),
    GCP_WEST("gcpwest", "/test/qtp/config/vdcConfig.json", "UAT3-GCP","gcp-vdc-dev-test","/root/.kube/config"),
    AZURE_WEST("azurewest", "/test/qtp/config/vdcConfig.json", "UAT3-AZURE","uat-az-milvus-westus3-1","/root/.kube/config"),
    ALI_HZ("alihz", "/test/qtp/config/vdcConfig.json", "UAT","","/root/.kube/config"),
    TC_NJ("tcnj", "/test/qtp/config/vdcConfig.json", "UAT","","/root/.kube/config"),
    HWC("hwc", "/test/qtp/config/vdcConfig.json", "UAT","","/root/.kube/config");

    public final String region;
    public final String vdcConfigPath;
    public final String envNodeName;

    public final String cluster;
    public final String kubeConfig;

    EnvEnum(String region, String vdcConfigPath, String envNodeName, String cluster, String kubeConfig) {
        this.region = region;
        this.vdcConfigPath = vdcConfigPath;
        this.envNodeName = envNodeName;
        this.cluster = cluster;
        this.kubeConfig = kubeConfig;
    }

    public static EnvEnum getEnvByName(String name) {
        return Arrays.stream(EnvEnum.values()).filter(x -> x.region.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
}
