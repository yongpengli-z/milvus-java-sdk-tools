package custom.entity;

import lombok.Data;

/**
 * Describe Collection 参数。
 * <p>
 * 对应前端组件：`describeCollectionEdit.vue`
 */
@Data
public class DescribeCollectionParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`describeCollectionEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`describeCollectionEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;
}
