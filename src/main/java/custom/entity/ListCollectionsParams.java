package custom.entity;

import lombok.Data;

/**
 * List Collections 参数。
 * <p>
 * 对应前端组件：`listCollectionsEdit.vue`
 */
@Data
public class ListCollectionsParams {
    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`listCollectionsEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：使用当前连接的 database。
     */
    String databaseName;
}
