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
     * </ul>
     * 前端：`searchEdit.vue` -> "Collection Rule"
     * <p>
     * 前端默认值：""（None）
     */
    private String collectionRule;
}
