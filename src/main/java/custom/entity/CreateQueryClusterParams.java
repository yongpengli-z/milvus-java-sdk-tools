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
    // 实例保留小时数（QTP 到期清理用，组件本身不消费）。
    // On-Demand 实例默认留 24h，避免 useHours=0 导致任务跑完即删。
    int useHours = 24;
    Integer maxQueryNodeCU;
    Integer maxQueryNodeReplicas;

    String vectorLakeDbVersion;
    String queryClusterDbVersion;
}
