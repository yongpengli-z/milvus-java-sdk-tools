package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

/**
 * Search（向量检索）参数。
 * <p>
 * 对应前端组件：`searchEdit.vue`
 */
@Data
public class SearchParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`searchEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * NQ（query 向量数量）。
     * <p>
     * 前端：`searchEdit.vue` -> "NQ"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1
     */
    private int nq;

    /**
     * TopK（每个 query 返回的候选数量）。
     * <p>
     * 前端：`searchEdit.vue` -> "TopK"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1
     */
    private int topK;

    /**
     * 是否每次请求随机选择 query 向量。
     * <p>
     * 前端：`searchEdit.vue` -> "RandomVector"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：true
     */
    private boolean randomVector;

    /**
     * 输出字段列表（outputFields）。
     * <p>
     * 前端：`searchEdit.vue` -> "Outputs"（逗号分隔输入）
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> outputs;

    /**
     * 标量过滤表达式（Milvus expr / filter）。
     * <p>
     * 前端：`searchEdit.vue` -> "Filter"
     * <p>
     * 支持占位符：`$fieldName`（配合 {@link #generalFilterRoleList} 运行时替换）。
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String filter;

    /**
     * 并发线程数。
     * <p>
     * 前端：`searchEdit.vue` -> "Concurrency Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：10
     */
    private int numConcurrency;

    /**
     * 运行时长（分钟）。
     * <p>
     * 前端：`searchEdit.vue` -> "Running Time(Minutes)"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：10
     * <p>
     * 说明：Search 是按时间循环请求；该值通常需要 > 0。
     */
    private long runningMinutes;

    /**
     * 运行次数（可选，每个线程的请求次数）。
     * <p>
     * 前端：`searchEdit.vue` -> "Running Count"
     * <p>
     * 前端默认值：0
     * <ul>
     *   <li>0：按 runningMinutes 时间模式跑（默认行为）</li>
     *   <li>&gt;0：次数模式，每个线程跑满 N 次后停止（次数优先，不再看时间）</li>
     * </ul>
     * 只跑一次：numConcurrency=1 且 runningCount=1。
     */
    private long runningCount;

    /**
     * Search Level（会写入 searchParams: {"level": x}）。
     * <p>
     * 前端：`searchEdit.vue` -> "Search Level"
     * <p>
     * 前端默认值：1
     */
    private int searchLevel;

    /**
     * IndexAlgo（可选，会写入 searchParams: {"index_algo": "..."}）。
     * <p>
     * 前端：`searchEdit.vue` -> "IndexAlgo"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String indexAlgo;

    /**
     * 向量字段名（annsField）。
     * <p>
     * 前端：`searchEdit.vue` -> "AnnsField"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：`vectorField_1`
     */
    private String annsField;

    /**
     * 目标 QPS（每线程 RateLimiter 限流；0 表示不限制）。
     * <p>
     * 前端：`searchEdit.vue` -> "Target QPS"
     * <p>
     * 前端默认值：0
     */
    private double targetQps;

    /**
     * filter 占位符替换规则列表（高级用法）。
     * <p>
     * 前端：`searchEdit.vue` -> Filter 的 "Edit" 面板
     * <p>
     * 前端默认值：包含 1 条空规则（fieldName/prefix/sequenceOrRandom 为空，randomRangeParamsList 含 1 条空 range）。
     */
    private List<GeneralDataRole> generalFilterRoleList;

    /**
     * 是否忽略错误继续搜索。
     * <p>
     * 前端：`searchEdit.vue` -> "Ignore error"
     * <p>
     * 前端默认值：false
     */
    private boolean ignoreError;

    /**
     * Collection 选择规则（可选）：
     * <ul>
     *   <li>""：默认使用最近一次创建/记录的 collection</li>
     *   <li>"random"：从全局 collection 列表随机选</li>
     *   <li>"sequence"：按顺序轮询全局 collection 列表</li>
     *   <li>"sequence_per_request"：每个请求轮换取下一个 collection（全局原子游标，跨线程唯一；
     *       总请求数 ≤ 池子大小时每个 collection 恰好被 search 一次，常用于测多 collection 并发上限 QPS）</li>
     * </ul>
     * 前端：`searchEdit.vue` -> "Collection Rule"
     * <p>
     * 前端默认值：""（None）
     */
    private String collectionRule;

    /**
     * Collection 名称前缀（可选）。
     * <p>
     * 前端：`searchEdit.vue` -> "Collection Name Prefix"
     * <p>
     * 前端默认值：""（空字符串，不过滤）
     * <p>
     * 非空时：先按前缀过滤全局 collection 池，再做 sequence/random 选择；
     * 未匹配到任何 collection 会直接报错。
     */
    private String collectionNamePrefix;

    /**
     * Collection 池区间起始下标（可选，默认 -1 不启用）。
     * <p>
     * 前端：`searchEdit.vue` -> "Collection Range Start"
     * <p>
     * >= 0 时启用区间模式：前缀过滤后先按名称排序，再取 [rangeStart, rangeEnd) 切片，
     * 用于多 client 物理分割（如 client0 取 [0,334)，client1 取 [334,668)）。
     */
    private int collectionRangeStart = -1;

    /**
     * Collection 池区间结束下标（可选，开区间）。
     * <p>
     * 前端：`searchEdit.vue` -> "Collection Range End"
     * <p>
     * 前端默认值：-1（或 0/超出池子大小）表示取到末尾。
     */
    private int collectionRangeEnd = -1;

    /**
     * 查询分区列表（可选）。
     * <p>
     * 前端：`searchEdit.vue` -> "Partition Names"（逗号分隔输入）
     * <p>
     * 前端默认值：""（空字符串，占位；失焦后会被 split 成数组）
     * <p>
     * 建议：生成 JSON 时使用 [] 或 ["p1","p2"]。
     */
    private List<String> partitionNames;

    /**
     * SDK 请求超时时间（毫秒）。
     * <p>
     * 前端：`searchEdit.vue` -> "Timeout(ms)"
     * <p>
     * 前端默认值：800
     * <p>
     * 说明：每次 search 请求的超时时间，0 表示使用默认值 800ms。
     */
    private long timeout;

    /**
     * 目标 endpoint（可选，用于 Global Cluster 场景）。
     * <ul>
     *   <li>"" / null / "primary" — 使用默认 primary client</li>
     *   <li>"global" — 使用 GDN 统一入口</li>
     *   <li>"secondary" — 使用第一个 secondary</li>
     *   <li>"secondary_0" / "secondary_1" — 使用指定下标的 secondary</li>
     *   <li>以 "https://" 开头 — 直接连该 URI</li>
     * </ul>
     * 前端默认值：""（空字符串，使用 primary）
     */
    private String targetEndpoint;

    /**
     * Query 数据集名称（可选），对应 {@link custom.common.QueryDatasetEnum} 中的 datasetName。
     * <p>
     * 指定后：search 的查询输入（向量/文本）从该数据集文件全量加载，不再从 collection 底库捞取。
     * <ul>
     *   <li>"widetable"：稠密向量查询，读取 emb_*.npy（768 维 FloatVec）</li>
     *   <li>"widetable_bm25"：BM25 文本查询，读取 bm25_title_*.txt（EmbeddedText）</li>
     * </ul>
     * 为空 / null 时：保持原有逻辑（从 collection 捞取 1000 条）。
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String queryDataset;
}
