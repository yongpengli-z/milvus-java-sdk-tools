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

}
