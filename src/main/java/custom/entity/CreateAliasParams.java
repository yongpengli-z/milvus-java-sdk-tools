package custom.entity;

import lombok.Data;

/**
 * Create Alias 参数。
 * <p>
 * 对应前端组件：`createAliasEdit.vue`
 */
@Data
public class CreateAliasParams {
    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`createAliasEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;

    /**
     * Collection 名称。
     * <p>
     * 前端：`createAliasEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * Alias 名称。
     * <p>
     * 前端：`createAliasEdit.vue` -> "Alias"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String alias;
}
