package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

/**
 * Query（标量查询）参数。
 * <p>
 * 对应前端组件：`queryEdit.vue`
 */
@Data
public class QueryParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`queryEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * 输出字段列表（outputFields）。
     * <p>
     * 前端：`queryEdit.vue` -> "Outputs"（逗号分隔输入）
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> outputs;

    /**
     * 查询过滤表达式（Milvus expr / filter）。
     * <p>
     * 前端：`queryEdit.vue` -> "Filter"
     * <p>
     * 支持占位符：`$fieldName`（配合 {@link #generalFilterRoleList} 运行时替换）。
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String filter;

    /**
     * 指定要查询的主键 ID 列表（可选）。
     * <p>
     * 前端：`queryEdit.vue` -> "IDs"（逗号分隔输入）
     * <p>
     * 空数组表示不按 ids 查询（仅使用 filter/limit/offset 等）。
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<Object> ids;

    /**
     * 并发线程数。
     * <p>
     * 前端：`queryEdit.vue` -> "Concurrency Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：10
     */
    private int numConcurrency;

    /**
     * 运行时长（分钟）。
     * <p>
     * 前端：`queryEdit.vue` -> "Running time(Minutes)"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：10
     */
    private long runningMinutes;

    /**
     * 单次查询返回条数上限（limit）。
     * <p>
     * 前端：`queryEdit.vue` -> "Limit"
     * <p>
     * 前端默认值：""（空字符串，占位；页面输入框是字符串）
     * <p>
     * 建议：生成 JSON 时 limit 传数字（例如 0/10/100），或不传。
     */
    private long limit;

    /**
     * 查询分区列表（可选）。
     * <p>
     * 前端：`queryEdit.vue` -> "Partition Names"（逗号分隔输入）
     * <p>
     * 前端默认值：""（空字符串，占位；失焦后会被 split 成数组）
     * <p>
     * 建议：生成 JSON 时使用 [] 或 ["p1","p2"]。
     */
    private List<String> partitionNames;

    /**
     * 查询偏移量（offset）。
     * <p>
     * 前端：`queryEdit.vue` -> "Offset"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：0
     */
    private long offset;

    /**
     * filter 占位符替换规则列表（高级用法）。
     * <p>
     * 前端：`queryEdit.vue` -> Filter 的 "Edit" 面板
     * <p>
     * 前端默认值：包含 1 条空规则（fieldName/prefix/sequenceOrRandom 为空，randomRangeParamsList 含 1 条空 range）。
     */
    private List<GeneralDataRole> generalFilterRoleList;

    /**
     * 目标 QPS（每线程 RateLimiter 限流；0 表示不限制）。
     * <p>
     * 前端：`queryEdit.vue` -> "Target QPS"
     * <p>
     * 前端默认值：0
     */
    private double targetQps;

    /**
     * Collection 选择规则（可选）：
     * <ul>
     *   <li>""：默认使用最近一次创建/记录的 collection</li>
     *   <li>"random"：从全局 collection 列表随机选</li>
     *   <li>"sequence"：按顺序轮询全局 collection 列表</li>
     * </ul>
     * 前端：`queryEdit.vue` -> "Collection Rule"
     * <p>
     * 前端默认值：""（None）
     */
    private String collectionRule;
}
