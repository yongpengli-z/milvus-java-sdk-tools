package custom.entity;

import lombok.Data;

/**
 * Rename Collection 参数。
 * <p>
 * 对应前端组件：`renameCollectionEdit.vue`
 */
@Data
public class RenameCollectionParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`renameCollectionEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * 新 collection 名称。
     * <p>
     * 前端：`renameCollectionEdit.vue` -> "New CollectionName"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String newCollectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`renameCollectionEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;
}
