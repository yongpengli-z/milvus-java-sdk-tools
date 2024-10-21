package custom.entity;

import lombok.Data;

@Data
public class UpsertParams {
    private String collectionName;
    private String partitionName;
    private long startId;
    private long numEntries;
    private long batchSize;
    private int numConcurrency;
    private String dataset;
}