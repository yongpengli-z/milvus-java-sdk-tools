package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * SearchIterator 参数（迭代式搜索，用于大结果集分页拉取）。
 * <p>
 * 对应前端组件：`searchIteratorEdit.vue`
 */
@Data
public class SearchIteratorParams {
    /**
     * Collection 名称。
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     * <p>
     * 默认值：""（空字符串）
     */
    private String collectionName;

    /**
     * NQ（query 向量数量）。
     * <p>
     * 默认值：1
     */
    private int nq;

    /**
     * TopK。
     * <p>
     * 默认值：1
     */
    private int topK;

    /**
     * 是否每次请求随机选择 query 向量。
     * <p>
     * 默认值：true
     */
    private boolean randomVector;

    /**
     * SearchIterator 使用的向量字段名（vectorFieldName）。
     * <p>
     * 默认值：""（空字符串）
     */
    private String vectorFieldName;

    /**
     * 输出字段列表（outputFields）。
     * <p>
     * 默认值：[]（空数组）
     */
    private List<String> outputs;

    /**
     * 过滤表达式（expr）。
     * <p>
     * 默认值：""（空字符串）
     */
    private String filter;

    /**
     * MetricType（字符串形式：IP/COSINE/L2；其它默认 L2）。
     * <p>
     * 默认值：`L2`
     */
    private String metricType;

    /**
     * 并发线程数。
     * <p>
     * 默认值：10
     */
    private int numConcurrency;

    /**
     * 运行时长（分钟）。
     * <p>
     * 默认值：10
     */
    private long runningMinutes;

    /**
     * iterator 参数（JSON 字符串），例如：{"level": 1}。
     * <p>
     * 默认值：`{"level": 1}`
     */
    private String params;

    /**
     * IndexAlgo（可选）。
     * <p>
     * 默认值：""（空字符串）
     */
    private String indexAlgo;

    /**
     * iterator 每次拉取 batch size。
     * <p>
     * 默认值：10
     */
    private int batchSize;

    /**
     * 是否使用 V1（当前实现保留字段，默认 false）。
     * <p>
     * 默认值：false
     */
    private boolean useV1;

    /**
     * 用于从 collection 中抽样向量的字段名（annsFields）。
     * <p>
     * 说明：后端会先 query 该字段以获取 query vectors。
     * <p>
     * 默认值：""（空字符串）
     */
    private String annsFields;

    /**
     * 目标 endpoint（可选，用于 Global Cluster 场景）。
     * <ul>
     *   <li>"" / null / "primary" — 使用默认 primary client</li>
     *   <li>"global" — 使用 GDN 统一入口</li>
     *   <li>"secondary" — 使用第一个 secondary</li>
     *   <li>"secondary_0" / "secondary_1" — 使用指定下标的 secondary</li>
     *   <li>以 "https://" 或 "http://" 开头 — 直接连该 URI</li>
     * </ul>
     */
    private String targetEndpoint;
}
