package custom.entity;

import lombok.Data;

/**
 * Drop Collection 参数。
 * <p>
 * 对应前端组件：`dropCollectionEdit.vue`
 */
@Data
public class DropCollectionParams {
    /**
     * 是否删除实例内所有 collection。
     * <p>
     * 前端：`dropCollectionEdit.vue` -> "Drop all collections"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean dropAll;

    /**
     * Collection 名称（当 {@link #dropAll}=false 时使用）。
     * <p>
     * 前端：`dropCollectionEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String collectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`dropCollectionEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String databaseName;
}
