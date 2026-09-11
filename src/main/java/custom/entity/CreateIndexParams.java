package custom.entity;


import lombok.Data;

import java.util.List;

/**
 * 创建索引参数。
 * <p>
 * 对应前端组件：`createIndexEdit.vue`
 */
@Data
public class CreateIndexParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`createIndexEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`createIndexEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String databaseName;

    /**
     * Index 参数列表。
     * <p>
     * 前端：`createIndexEdit.vue` -> "IndexParamList"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1 条（fieldName=`FloatVector_1`, indexType=`AUTOINDEX`, metricType=`L2`, buildLevel=`1`）。
     */
    private List<IndexParams> indexParams;

    /**
     * Collection 名称前缀过滤（可选）。
     * <p>
     * 非空时进入多 collection 模式：对 globalCollectionNames 池子按前缀过滤后逐个建索引，
     * 与 {@link #collectionRangeStart}/{@link #collectionRangeEnd} 可叠加（先前缀、再区间过滤）。
     * <p>
     * 前端默认值：""
     */
    private String collectionNamePrefix;

    /**
     * Collection 区间起始（可选，默认 -1 不启用）。
     * >=0 时进入区间模式。若前缀命中的名称是 前缀+纯数字后缀，按后缀数值过滤 [rangeStart, rangeEnd)，
     * 前导零不影响；否则按名称排序后取下标切片。
     */
    private int collectionRangeStart = -1;

    /**
     * Collection 区间结束（开区间，可选，默认 -1）。
     * <=0 表示不限制上界/取到末尾。
     */
    private int collectionRangeEnd = -1;

    /**
     * 建索引并发度（可选，默认 1 串行）。
     * <p>
     * >1 时起固定数量 worker 线程并发建索引，所有 worker 从共享游标抢任务，
     * 每个 collection 只被一个线程处理一次。实际并发度 = min(numConcurrency, collection 数)。
     * 不限制上限，请根据被测实例规格合理设置。
     * <p>
     * 前端默认值：1
     */
    private int numConcurrency = 1;

}
