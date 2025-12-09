package custom.entity;

import lombok.Data;

@Data
public class RestoreBackupParams {
    private String backupId;
    private String fromInstanceId;
    private boolean notChangeStatus;
    private int restorePolicy;
    private boolean skipCreateCollection;
    private String toInstanceId;
    private boolean truncateBinlogByTs;
    private boolean withRBAC;

}
