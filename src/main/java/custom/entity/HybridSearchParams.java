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
     * </ul>
     * <p>
     * 前端默认值：""（None）
     */
    private String collectionRule;

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
    }
}

