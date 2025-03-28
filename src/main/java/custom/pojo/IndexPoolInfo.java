package custom.pojo;

import lombok.Data;

import java.util.List;

@Data
public class IndexPoolInfo {
    int id;
    String regionId;
    String k8sCluster;
    String name;
    String managerImageTag;
    String workerImageTag;
    List<Integer> indexTypes;
    int architecture;
    String domain;
    int port;
    int status;
    String checkSchedule;
    int freeNum;
    int maxIndexNode;
    int maxWaitingTask;
    String description;
    int scalingStrategy;
}
