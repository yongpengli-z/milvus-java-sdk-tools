package custom.entity;

import lombok.Data;

@Data
public class CreateQueryClusterParams {
    String clusterName;
    int cuSize = 8;
    String projectId;
    String projectName;
    String regionId;
    String sessionTTL = "30m";
    Integer maxQueryNodeCU;
    Integer maxQueryNodeReplicas;

    String vectorLakeDbVersion;
    String queryClusterDbVersion;
}
