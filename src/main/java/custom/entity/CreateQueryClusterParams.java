package custom.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class CreateQueryClusterParams {
    String clusterName;
    int cuSize = 8;
    String projectId;
    String projectName;
    String regionId;
    String sessionTTL = "60s";
    Integer maxQueryNodeCU;
    Integer maxQueryNodeReplicas;

    String vectorLakeDbVersion;
    boolean autoCreateVectorLake = true;
    boolean autoUpgradeVectorLake = false;

    String queryClusterDbVersion;
    boolean autoUpgradeQueryCluster = false;
    boolean forceUpgradeQueryCluster = true;

    @JSONField(serialize = false)
    String apiKey;
    boolean usePersonalApiKey = true;
    boolean connectAfterCreate = true;

    String accountEmail;
    @JSONField(serialize = false)
    String accountPassword;
}
