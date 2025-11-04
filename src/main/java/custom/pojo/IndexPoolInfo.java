package custom.pojo;

import lombok.Data;

import java.util.List;

@Data
public class IndexPoolInfo {
    int id;
    String regionId;
    String k8sCluster;
    String k8sNamespace;
    String name;
    String imageTag;
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
    String freePercent;
    String minFreeNum;
    private int maxWaitingTimeSeconds;
    private int freeSlots;
    private int maxSlots;
    private int strideSlots;
    private int workerRole;
    private List<WorkerSpec> workerSpecs;

    @Data
    public static class WorkerSpec{
        int id;
        int indexClusterId;
        int maxSlots;
        String requestsCpu;
        String requestsMemory;
        String limitsCpu;
        String limitsMemory;
        boolean enable;
    }
}
