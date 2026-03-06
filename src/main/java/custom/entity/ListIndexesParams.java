package custom.entity;

import lombok.Data;

/**
 * List Indexes 参数。
 * <p>
 * 对应前端组件：`listIndexesEdit.vue`
 */
@Data
public class ListIndexesParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`listIndexesEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`listIndexesEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;
}
