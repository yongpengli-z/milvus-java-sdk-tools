package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * HybridSearch（混合搜索）参数。
 * <p>
 * HybridSearch 支持在同一个 collection 中对多个向量字段进行搜索，并使用融合策略合并结果。
 * <p>
 * 对应前端组件：`hybridSearchEdit.vue`（如果前端有实现）
 */
@Data
public class HybridSearchParams {
    /**
     * Collection 名称。
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Collection 选择规则（可选）：
     * <ul>
     *   <li>""：默认使用最近一次创建/记录的 collection</li>
     *   <li>"random"：从全局 collection 列表随机选</li>
     *   <li>"sequence"：按顺序轮询全局 collection 列表</li>
     *   <li>"sequence_per_request"：每个 hybridSearch 请求轮换取下一个 collection（全局原子游标，跨线程唯一；
     *       总请求数 ≤ 池子大小时每个 collection 恰好被搜一次）</li>
     * </ul>
     * <p>
     * 前端默认值：""（None）
     */
    private String collectionRule;

    /**
     * Collection 名称前缀（可选）。
     * <p>
     * 前端默认值：""（空字符串，不过滤）
     * <p>
     * 非空时：先按前缀过滤全局 collection 池，再做 sequence/random 选择；
     * 未匹配到任何 collection 会直接报错。
     */
    private String collectionNamePrefix;

    /**
     * Collection 池区间起始（可选，默认 -1 不启用）。
     * >= 0 时启用区间模式。若前缀命中的名称是 前缀+纯数字后缀（如 multi_tenant_1000_0000001），
     * 按后缀数值过滤 [rangeStart, rangeEnd)，前导零不影响（填 1 即匹配 ..._0000001）；
     * 否则退化为按名称排序后取下标切片，用于多 client 物理分割（如 client0 取 [0,334)，client1 取 [334,668)）。
     */
    private int collectionRangeStart = -1;

    /**
     * Collection 池区间结束（可选，开区间）。
     * 默认值 -1（或 0）表示不限制上界/取到末尾。
     */
    private int collectionRangeEnd = -1;

    /**
     * 混合搜索请求列表。
     * <p>
     * 每个元素代表一个向量字段的搜索请求，包含：
     * <ul>
     *   <li>annsField：向量字段名</li>
     *   <li>vectors：查询向量列表（List of BaseVector）</li>
     *   <li>topK：该字段的 topK</li>
     *   <li>metricType：距离度量类型（L2/IP/COSINE/HAMMING/JACCARD/BM25）</li>
     *   <li>searchParams：搜索参数（Map，例如 {"level": 1}）</li>
     * </ul>
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：[]（空数组，但实际使用时必须至少包含一个搜索请求）
     */
    private List<HybridSearchRequest> searchRequests;

    /**
     * 融合策略类型。
     * <p>
     * 可选值：
     * <ul>
     *   <li>"RRF"：Reciprocal Rank Fusion（倒数排名融合）</li>
     *   <li>"WeightedRanker"：加权排序</li>
     * </ul>
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值："RRF"
     */
    private String ranker;

    /**
     * 融合策略参数（Map）。
     * <p>
     * 根据 ranker 类型不同，参数也不同：
     * <ul>
     *   <li>RRF：{"k": 60}（k 为常数，默认 60）</li>
     *   <li>WeightedRanker：{"weights": [0.5, 0.5]}（权重列表，长度需与 searchRequests 数量一致）</li>
     * </ul>
     * <p>
     * 前端默认值：{}（空对象，使用默认参数）
     */
    private Map<String, Object> rankerParams;

    /**
     * TopK（最终返回的候选数量）。
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：10
     */
    private int topK;

    /**
     * NQ（query 向量数量，用于从数据集中采样向量）。
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1
     */
    private int nq;

    /**
     * 是否每次请求随机选择 query 向量。
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：true
     */
    private boolean randomVector;

    /**
     * 输出字段列表（outputFields）。
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> outputs;

    /**
     * 并发线程数。
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：10
     */
    private int numConcurrency;

    /**
     * 运行时长（分钟）。
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：10
     * <p>
     * 说明：HybridSearch 是按时间循环请求；该值通常需要 > 0。
     */
    private long runningMinutes;

    /**
     * 运行次数（可选，每个线程的请求次数）。
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
     * 目标 QPS（每线程 RateLimiter 限流；0 表示不限制）。
     * <p>
     * 前端默认值：0
     */
    private double targetQps;

    /**
     * filter 占位符替换规则列表（高级用法）。
     * <p>
     * 前端默认值：包含 1 条空规则（fieldName/prefix/sequenceOrRandom 为空，randomRangeParamsList 含 1 条空 range）。
     */
    private List<GeneralDataRole> generalFilterRoleList;

    /**
     * 是否忽略错误继续搜索。
     * <p>
     * 前端默认值：false
     */
    private boolean ignoreError;

    /**
     * SDK 请求超时时间（毫秒）。
     * <p>
     * 前端：`hybridSearchEdit.vue` -> "Timeout(ms)"
     * <p>
     * 前端默认值：3000
     * <p>
     * 说明：每次 hybridSearch 请求的超时时间，0 表示使用默认值 3000ms。
     */
    private long timeout;

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

    /**
     * 单个混合搜索请求的参数。
     * <p>
     * 用于 {@link HybridSearchParams#searchRequests}。
     */
    @Data
    public static class HybridSearchRequest {
        /**
         * 向量字段名（annsField）。
         * <p>
         * 前端必填：是
         */
        private String annsField;

        /**
         * 该字段的 TopK。
         * <p>
         * 前端必填：是
         * <p>
         * 前端默认值：10
         */
        private int topK;

        /**
         * 距离度量类型（MetricType）。
         * <p>
         * 可选值：L2、IP、COSINE、HAMMING、JACCARD、BM25
         * <p>
         * 前端必填：是
         * <p>
         * 前端默认值："L2"
         */
        private String metricType;

        /**
         * 搜索参数（Map）。
         * <p>
         * 例如：{"level": 1} 或 {"level": 1, "index_algo": "..."}
         * <p>
         * 前端默认值：{}（空对象）
         */
        private Map<String, Object> searchParams;

        /**
         * 标量过滤表达式（Milvus expr / filter）。
         * <p>
         * 支持占位符：`$fieldName`（配合 {@link HybridSearchParams#generalFilterRoleList} 运行时替换）。
         * <p>
         * 每个 HybridSearchRequest 可以有自己的 filter，用于对该字段的搜索结果进行过滤。
         * <p>
         * 前端默认值：""（空字符串，表示不过滤）
         */
        private String filter;

        /**
         * Query 数据集名称（可选），对应 {@link custom.common.QueryDatasetEnum} 中的 datasetName。
         * <p>
         * 指定后：该字段的查询输入（向量/文本）从数据集文件全量加载，不再从 collection 底库捞取。
         * 不同字段可配置不同的 query 数据集（如 dense 字段用 "widetable"，BM25 字段用 "widetable_bm25"）。
         * <p>
         * 前端默认值：""（空字符串，保持原有逻辑）
         */
        private String queryDataset;
    }
}
