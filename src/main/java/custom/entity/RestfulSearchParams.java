package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

/**
 * RESTful Search（通过 Milvus RESTful API 进行向量检索）参数。
 * <p>
 * 与 {@link SearchParams} 功能类似，但通过 HTTP RESTful 接口而非 Java SDK 发起请求。
 * 复用 {@link custom.entity.result.SearchResultA} 作为返回结果。
 */
@Data
public class RestfulSearchParams {
    /**
     * Collection 名称。为空时默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * NQ（query 向量数量）。默认值：1
     */
    private int nq;

    /**
     * TopK（每个 query 返回的候选数量）。默认值：1
     */
    private int topK;

    /**
     * 是否每次请求随机选择 query 向量。默认值：true
     */
    private boolean randomVector;

    /**
     * 输出字段列表（outputFields）。
     */
    private List<String> outputs;

    /**
     * 标量过滤表达式。
     * 支持占位符：`$fieldName`（配合 {@link #generalFilterRoleList} 运行时替换）。
     */
    private String filter;

    /**
     * 并发线程数。默认值：10
     */
    private int numConcurrency;

    /**
     * 运行时长（分钟）。默认值：10
     */
    private long runningMinutes;

    /**
     * Search Level（会写入 searchParams: {"level": x}）。默认值：1
     */
    private int searchLevel;

    /**
     * 向量字段名（annsField）。默认值：vectorField_1
     */
    private String annsField;

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
     * Collection 选择规则：""=默认, "random"=随机, "sequence"=轮询。
     */
    private String collectionRule;

    /**
     * 查询分区列表（可选）。
     */
    private List<String> partitionNames;
}
