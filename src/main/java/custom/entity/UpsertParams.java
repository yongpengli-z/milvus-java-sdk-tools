package custom.entity;

import custom.pojo.FieldDataSource;
import custom.pojo.GeneralDataRole;
import custom.pojo.UpdateFieldName;
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
     * <p>
     * 注意：当设置了 {@link #collectionNamePrefix}（非空）或 {@link #collectionRangeStart}（>=0）时，
     * 进入「多 collection 模式」，会忽略本字段，改为对池中命中的**每个** collection 各 upsert
     * {@link #numEntries} 条数据。
     */
    private String collectionRule;

    /**
     * Collection 名称前缀过滤（可选）。
     * <p>
     * 非空时进入多 collection 模式：对 globalCollectionNames 池子按前缀过滤后，
     * 逐个 collection 各 upsert {@link #numEntries} 条数据；
     * 与 {@link #collectionRangeStart}/{@link #collectionRangeEnd} 可叠加（先前缀、再区间过滤）。
     * <p>
     * 前端默认值：""
     */
    private String collectionNamePrefix;

    /**
     * Collection 区间起始（可选，默认 -1 不启用）。
     * >=0 时进入多 collection 区间模式。若前缀命中的名称是 前缀+纯数字后缀，按后缀数值过滤 [rangeStart, rangeEnd)，
     * 前导零不影响；否则按名称排序后取下标切片。
     */
    private int collectionRangeStart = -1;

    /**
     * Collection 区间结束（开区间，可选，默认 -1）。
     * <=0 表示不限制上界/取到末尾。
     */
    private int collectionRangeEnd = -1;

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

    /**
     * 随机长度系数（0~1 之间）。
     * <p>
     * 当该值 > 0 时，所有随机长度（VarChar 长度、Array capacity 等）= 原始上限 * lengthFactor。
     * 默认值：0（不启用，使用原始随机长度）
     */
    private double lengthFactor;

    /**
     * Nullable 字段的 null 值比例（0~1 之间）。
     * <p>
     * 说明：0 表示不生成 null 值，1 表示全部为 null，0.5 表示约 50% 的行为 null。
     * 仅对 isNullable=true 的字段生效。
     */
    private double nullableRatio = 0.5;

    /**
     * 是否启用 Partial Update（部分更新）。
     * <p>
     * 启用后，仅更新 updateFieldNames 中指定的字段，其余字段保持不变。
     * <p>
     * 前端：`upsertEdit.vue` -> "Partial Update"
     * <p>
     * 前端默认值：false
     */
    private boolean partialUpdate;

    /**
     * Partial Update 时需要更新的字段名列表（不含主键，主键会自动包含）。
     * <p>
     * 仅当 partialUpdate=true 时生效。
     * <p>
     * 前端：`upsertEdit.vue` -> "Update Fields"
     * <p>
     * 前端默认值：空列表
     */
    private List<UpdateFieldName> updateFieldNames;

    /**
     * PK 来源过滤表达式（可选）。
     * <p>
     * 非空时：upsert 前先按该 filter 查询 collection 现有主键（单次查询上限 min(numEntries, 16384)），
     * 用查到的真实 PK 作为 upsert 行的主键（行数多于 PK 数时循环复用），用于验证"upsert 已有行"场景
     * （如 autoID 集合的 PK 保留，PR#53158）。
     * <p>
     * 前端：`upsertEdit.vue` -> "PK From Filter"
     * <p>
     * 前端默认值：""（空字符串，保持原逻辑：pk 按 startId+countIndex 生成）
     */
    private String pkFromFilter;

    /**
     * upsert 完成后校验发送的 PK 集合是否原样保留（仅 {@link #pkFromFilter} 非空时生效）。
     * <p>
     * 用同一 filter 重新查询 PK 集合，与发送集合双向比对（缺失/新增都报），结果写入 assertMessages。
     * <p>
     * 前端：`upsertEdit.vue` -> "Verify PK Preserved"
     * <p>
     * 前端默认值：false
     */
    private boolean verifyPkPreserved;

    /**
     * 目标 endpoint（可选，用于 Global Cluster 场景）。
     * <ul>
     *   <li>"" / null / "primary" — 使用默认 primary client</li>
     *   <li>"global" — 使用 GDN 统一入口</li>
     *   <li>"secondary" — 使用第一个 secondary</li>
     *   <li>"secondary_0" / "secondary_1" — 使用指定下标的 secondary</li>
     *   <li>以 "https://" 或 "http://" 开头 — 直接连该 URI</li>
     * </ul>
     * 前端默认值：""（空字符串，使用 primary）
     */
    private String targetEndpoint;
}
