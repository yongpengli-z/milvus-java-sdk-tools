package custom.entity;

import custom.pojo.FieldDataSource;
import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

/**
 * Insert（写入）参数。
 * <p>
 * 对应前端组件：`insertEdit.vue`
 */
@Data
public class InsertParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`insertEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Partition 名称（可选）。
     * <p>
     * 前端：`insertEdit.vue` -> "Partition Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String partitionName;

    /**
     * 起始主键/起始行号（用于生成/读取数据）。
     * <p>
     * 前端：`insertEdit.vue` -> "Start Id"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：0
     */
    private long startId;

    /**
     * 总写入条数（numEntries）。
     * <p>
     * 前端：`insertEdit.vue` -> "Entries Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1500000
     */
    private long numEntries;

    /**
     * 单次写入 batch size。
     * <p>
     * 前端：`insertEdit.vue` -> "Batch Size"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1000
     */
    private long batchSize;

    /**
     * 并发线程数。
     * <p>
     * 前端：`insertEdit.vue` -> "Concurrency Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1
     */
    private int numConcurrency;

    /**
     * 运行时长上限（分钟）。
     * <p>
     * 前端：`insertEdit.vue` -> "Running Minutes"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：0
     * <p>
     * 说明：当该值 > 0 时，Insert 线程会在达到时长后停止（可能早于 numEntries 写完）。
     */
    private long runningMinutes;

    /**
     * 禁写（deny write）后是否等待并重试。
     * <p>
     * 前端：`insertEdit.vue` -> "Retry after insert deny"
     * <p>
     * 前端默认值：false
     */
    private boolean retryAfterDeny;

    /**
     * 是否忽略错误继续写入。
     * <p>
     * 前端：`insertEdit.vue` -> "Ignore error"
     * <p>
     * 前端默认值：false
     */
    private boolean ignoreError;

    /**
     * 数据生成规则（高级用法）。
     * <p>
     * 前端：`insertEdit.vue` -> "AdvanceEdit"
     * <p>
     * 用于控制某些字段的取值分布（random/sequence + range+rate）。
     * <p>
     * 前端默认值：包含 1 条空规则（fieldName/prefix/sequenceOrRandom 为空，randomRangeParamsList 含 1 条空 range）。
     */
    private List<GeneralDataRole> generalDataRoleList;

    /**
     * Collection 选择规则（可选）：
     * <ul>
     *   <li>""：默认使用最近一次创建/记录的 collection</li>
     *   <li>"random"：从全局 collection 列表随机选</li>
     *   <li>"sequence"：按顺序轮询全局 collection 列表</li>
     * </ul>
     * 前端：`insertEdit.vue` -> "Collection Rule"
     * <p>
     * 前端默认值：""（None）
     */
    private String collectionRule;

    /**
     * 字段级别数据源配置（可选）。
     * <p>
     * 为指定字段配置独立的数据集来源，未配置的字段默认使用 random 生成。
     * <p>
     * 示例：[{"fieldName": "json_col", "dataset": "bluesky"}]
     * <p>
     * 前端默认值：null 或空列表
     */
    private List<FieldDataSource> fieldDataSourceList;
}
