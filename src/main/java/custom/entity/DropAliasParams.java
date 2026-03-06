package custom.entity;

import lombok.Data;

/**
 * Drop Alias 参数。
 * <p>
 * 对应前端组件：`dropAliasEdit.vue`
 */
@Data
public class DropAliasParams {
    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`dropAliasEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;

    /**
     * Alias 名称。
     * <p>
     * 前端：`dropAliasEdit.vue` -> "Alias"
     * <p>
     * 前端必填：是
     */
    String alias;
}
