package custom.entity;

import lombok.Data;

/**
 * Create Database 参数。
 * <p>
 * 对应前端组件：`createDatabaseEdit.vue`
 */
@Data
public class CreateDatabaseParams {
    /**
     * Database 名称。
     * <p>
     * 前端：`createDatabaseEdit.vue` -> "Database Name"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String databaseName;
}
