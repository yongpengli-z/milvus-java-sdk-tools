package custom.entity;

import lombok.Data;

/**
 * Alter Alias 参数（修改 alias 指向的 collection）。
 * <p>
 * 对应前端组件：`alterAliasEdit.vue`
 */
@Data
public class AlterAliasParams {
    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`alterAliasEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;

    /**
     * Collection 名称。
     * <p>
     * 前端：`alterAliasEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * Alias 别名。
     * <p>
     * 前端：`alterAliasEdit.vue` -> "Alias"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String alias;
}
