package custom.entity;

import lombok.Data;

@Data
public class InsertParams {
    private String collectionName;
    private long numEntries;
    private long batchSize;
    private int numConcurrency;
    private String dataset;
}
