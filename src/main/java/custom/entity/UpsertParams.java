package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

/**
 * Upsert（写入/更新）参数。
 * <p>
 * 对应前端组件：`upsertEdit.vue`
 */
@Data
public class UpsertParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`upsertEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Partition 名称（可选）。
     * <p>
     * 前端：`upsertEdit.vue` -> "Partition Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String partitionName;

    /**
     * 起始主键/起始行号（用于生成/读取数据）。
     * <p>
     * 前端：`upsertEdit.vue` -> "Start Id"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：0
     */
    private long startId;

    /**
     * 总 upsert 条数（numEntries）。
     * <p>
     * 前端：`upsertEdit.vue` -> "Entries Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1500000
     */
    private long numEntries;

    /**
     * 单次 upsert batch size。
     * <p>
     * 前端：`upsertEdit.vue` -> "Batch Size"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1000
     */
    private long batchSize;

    /**
     * 并发线程数。
     * <p>
     * 前端：`upsertEdit.vue` -> "Concurrency Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1
     */
    private int numConcurrency;

    /**
     * 数据集类型。
     * <p>
     * 前端：`upsertEdit.vue` -> "Dataset"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：`random`
     */
    private String dataset;

    /**
     * 运行时长上限（分钟）。
     * <p>
     * 说明：当该值 > 0 时，Upsert 线程会在达到时长后停止（可能早于 numEntries 写完）。
     * <p>
     * 前端默认值：0（当前 UI 未展示该输入项，但 params 中保留该字段）
     */
    private long runningMinutes;

    /**
     * 禁写（deny write）后是否等待并重试。
     * <p>
     * 前端：`upsertEdit.vue` -> "Retry after insert deny"
     * <p>
     * 前端默认值：false
     */
    private boolean retryAfterDeny;

    /**
     * 数据生成规则（高级用法）。
     * <p>
     * 前端：`upsertEdit.vue` -> "AdvanceEdit"
     * <p>
     * 前端默认值：包含 1 条空规则（fieldName/prefix/sequenceOrRandom 为空，randomRangeParamsList 含 1 条空 range）。
     */
    private List<GeneralDataRole> generalDataRoleList;

    /**
     * 目标 QPS（每线程 RateLimiter 限流；0 表示不限制）。
     * <p>
     * 前端：`upsertEdit.vue` -> "Target QPS"
     * <p>
     * 前端默认值：0
     */
    private int targetQps;

    /**
     * Collection 选择规则（可选）：
     * <ul>
     *   <li>""：默认使用最近一次创建/记录的 collection</li>
     *   <li>"random"：从全局 collection 列表随机选</li>
     *   <li>"sequence"：按顺序轮询全局 collection 列表</li>
     * </ul>
     * 前端：`upsertEdit.vue` -> "Collection Rule"
     * <p>
     * 前端默认值：""（None）
     */
    private String collectionRule;
}
