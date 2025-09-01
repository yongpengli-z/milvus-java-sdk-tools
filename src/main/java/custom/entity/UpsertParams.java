package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

@Data
public class UpsertParams {
    private String collectionName;
    private String partitionName;
    private long startId;
    private long numEntries;
    private long batchSize;
    private int numConcurrency;
    private String dataset;
    private long runningMinutes;
    private boolean retryAfterDeny;
    private List<GeneralDataRole> generalDataRoleList;
}
