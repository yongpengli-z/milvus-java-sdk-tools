package custom.entity;

import lombok.Data;

/**
 * Use Database 参数（切换当前 database）。
 * <p>
 * 对应前端组件：`useDatabaseEdit.vue`
 */
@Data
public class UseDatabaseParams {
    /**
     * Database 名称。
     * <p>
     * 后端字段名：`dataBaseName`
     * <p>
     * 前端：`useDatabaseEdit.vue` 使用的 JSON key 是 `databaseName`（注意与后端字段名不同）。
     * 如果前端直传 `databaseName`，需要确保后端/解析层能映射到该字段，否则会导致切库参数为空。
     * <p>
     * 前端默认值：""（空字符串）
     */
    String dataBaseName;
}
