package custom.entity;

import lombok.Data;

/**
 * RestoreBackup 参数（备份恢复，Cloud/内部环境）。
 * <p>
 * 对应前端组件：`restoreBackupEdit.vue`
 */
@Data
public class RestoreBackupParams {
    /**
     * 备份 ID。
     * <p>
     * 前端：`restoreBackupEdit.vue` -> "BackupId"
     * <p>
     * 前端必填：与 backupPreset 或 backupDataset/backupDim/backupRowCount 二选一
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String backupId;

    /**
     * 预置备份名称（可选）。
     * <p>
     * 页面推荐传环境无关的下拉值，后端会按当前环境从 BackupDatasetEnum 自动解析 backupId。
     * <p>
     * 示例："laion_768d_1m" 或 "laion_768d_8m"
     */
    private String backupPreset;

    /**
     * 预置备份的数据集名称（可选）。
     * <p>
     * 页面推荐使用该字段配合 backupDim、backupRowCount，由后端按当前环境自动选择 backupId。
     * <p>
     * 示例："laion"
     */
    private String backupDataset;

    /**
     * 预置备份的向量维度（可选）。
     * <p>
     * 示例：768
     */
    private int backupDim;

    /**
     * 预置备份的数据量（可选）。
     * <p>
     * 示例：1000000 表示 1m。
     */
    private long backupRowCount;

    /**
     * 源实例 ID（可选）。
     * <p>
     * 说明：后端可根据 backupId 反查并填充该字段（见 RestoreBackupComp）。
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String fromInstanceId;

    /**
     * 是否不改变任务状态（notChangeStatus）。
     * <p>
     * 前端：`restoreBackupEdit.vue` -> "NotChangeStatus"
     * <p>
     * 前端默认值：false
     */
    private boolean notChangeStatus;

    /**
     * 恢复策略（restorePolicy）。
     * <p>
     * 前端：`restoreBackupEdit.vue` -> "RestorePolicy"
     * <ul>
     *   <li>0：keep collection origin</li>
     *   <li>1：release all collection</li>
     *   <li>2：load all collection</li>
     * </ul>
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1（release all collection）
     */
    private int restorePolicy;

    /**
     * 是否跳过创建 collection（skipCreateCollection）。
     * <p>
     * 前端：`restoreBackupEdit.vue` -> "SkipCreateCollection"
     * <p>
     * 前端默认值：false
     */
    private boolean skipCreateCollection;

    /**
     * 目标实例 ID（toInstanceId）。
     * <p>
     * 前端：`restoreBackupEdit.vue` -> "ToInstanceId"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String toInstanceId;

    /**
     * 是否按时间戳截断 binlog（truncateBinlogByTs）。
     * <p>
     * 前端：`restoreBackupEdit.vue` -> "TruncateBinlogByTs"
     * <p>
     * 前端默认值：false
     */
    private boolean truncateBinlogByTs;

    /**
     * 是否带 RBAC（withRBAC）。
     * <p>
     * 前端：`restoreBackupEdit.vue` -> "WithRBAC"
     * <p>
     * 前端默认值：false
     */
    private boolean withRBAC;

}
