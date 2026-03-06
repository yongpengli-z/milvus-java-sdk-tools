package custom.entity;

import lombok.Data;

/**
 * Describe Alias 参数。
 * <p>
 * 对应前端组件：`describeAliasEdit.vue`
 */
@Data
public class DescribeAliasParams {
    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`describeAliasEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;

    /**
     * Alias 名称。
     * <p>
     * 前端：`describeAliasEdit.vue` -> "Alias"
     * <p>
     * 前端必填：是
     */
    String alias;
}
