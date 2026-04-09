package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RESTful HybridSearch（混合搜索）参数。
 * <p>
 * 通过 Milvus RESTful API（/v2/vectordb/entities/advanced_search）对同一个 collection
 * 中的多个向量字段进行搜索，并使用融合策略（RRF / WeightedRanker）合并结果。
 * <p>
 * 与 {@link HybridSearchParams} 字段对齐，但通过 HTTP RESTful 接口而非 Java SDK 发起请求。
 */
@Data
public class RestfulHybridSearchParams {
    /**
     * Collection 名称。为空时默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Collection 选择规则：""=默认, "random"=随机, "sequence"=轮询。
     */
    private String collectionRule;

    /**
     * 混合搜索请求列表。
     * <p>
     * 每个元素代表一个向量字段的搜索请求。
     */
    private List<RestfulHybridSearchRequest> searchRequests;

    /**
     * 融合策略类型。
     * <ul>
     *   <li>"RRF"：Reciprocal Rank Fusion（倒数排名融合）</li>
     *   <li>"WeightedRanker"：加权排序</li>
     * </ul>
     * 默认值："RRF"
     */
    private String ranker;

    /**
     * 融合策略参数（Map）。
     * <ul>
     *   <li>RRF：{"k": 60}</li>
     *   <li>WeightedRanker：{"weights": [0.5, 0.5]}</li>
     * </ul>
     */
    private Map<String, Object> rankerParams;

    /**
     * TopK（最终返回的候选数量）。默认值：10
     */
    private int topK;

    /**
     * NQ（query 向量数量）。默认值：1
     */
    private int nq;

    /**
     * 是否每次请求随机选择 query 向量。默认值：true
     */
    private boolean randomVector;

    /**
     * 输出字段列表（outputFields）。
     */
    private List<String> outputs;

    /**
     * 并发线程数。默认值：10
     */
    private int numConcurrency;

    /**
     * 运行时长（分钟）。默认值：10
     */
    private long runningMinutes;

    /**
     * 目标 QPS（全局 RateLimiter 限流；0 表示不限制）。
     */
    private double targetQps;

    /**
     * filter 占位符替换规则列表。
     */
    private List<GeneralDataRole> generalFilterRoleList;

    /**
     * 是否忽略错误继续搜索。默认值：false
     */
    private boolean ignoreError;

    /**
     * HTTP Socket 读取超时时间（毫秒）。默认值：5000（0 表示使用默认值）。
     */
    private int socketTimeout;

    /**
     * 单个混合搜索请求的参数。
     */
    @Data
    public static class RestfulHybridSearchRequest {
        /**
         * 向量字段名（annsField）。
         */
        private String annsField;

        /**
         * 该字段的 TopK。
         */
        private int topK;

        /**
         * 搜索参数（Map），例如 {"level": 1}
         */
        private Map<String, Object> searchParams;

        /**
         * 标量过滤表达式。支持占位符：`$fieldName`。
         */
        private String filter;
    }
}
