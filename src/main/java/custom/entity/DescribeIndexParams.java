package custom.entity;

import lombok.Data;

/**
 * Describe Index 参数。
 * <p>
 * 对应前端组件：`describeIndexEdit.vue`
 */
@Data
public class DescribeIndexParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`describeIndexEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`describeIndexEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;

    /**
     * 字段名称。
     * <p>
     * 前端：`describeIndexEdit.vue` -> "Field Name"
     * <p>
     * 前端必填：是
     */
    String fieldName;

    /**
     * 索引名称（可选）。
     * <p>
     * 前端：`describeIndexEdit.vue` -> "Index Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String indexName;
}
