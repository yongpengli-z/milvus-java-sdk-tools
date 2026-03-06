package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * QueryIterator 参数（迭代式查询，用于大结果集分页拉取）。
 * <p>
 * 对应前端组件：`queryIteratorEdit.vue`
 */
@Data
public class QueryIteratorParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * 过滤表达式。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Filter"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String filter;

    /**
     * 输出字段列表（outputFields）。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Output Fields"
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> outputFields;

    /**
     * iterator 每次拉取 batch size。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Batch Size"
     * <p>
     * 前端默认值：100
     */
    private int batchSize;

    /**
     * 最大返回总数（limit），0 表示不限制。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Limit"
     * <p>
     * 前端默认值：0
     */
    private long limit;

    /**
     * 并发线程数。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Num Concurrency"
     * <p>
     * 前端默认值：1
     */
    private int numConcurrency;

    /**
     * 运行时长（分钟）。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Running Minutes"
     * <p>
     * 前端默认值：1
     */
    private long runningMinutes;

    /**
     * Partition 名称列表（可选）。
     * <p>
     * 前端：`queryIteratorEdit.vue` -> "Partition Names"
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> partitionNames;
}
