package custom.entity;

import lombok.Data;

@Data
public class StreamingNodeParams {
    int replicaNum;
    String cpu;
    String memory;
    String disk;
}
