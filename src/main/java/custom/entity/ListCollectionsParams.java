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

    /**
     * Collection 名称前缀（可选）。
     * <p>
     * 前端：`listCollectionsEdit.vue` -> "Collection Name Prefix"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：返回所有 collection；非空时：只返回名称以该前缀开头的 collection。
     * 例如 `multi_tenant_1000_` 可匹配 `multi_tenant_1000_0000001` ~ `multi_tenant_1000_0500000`。
     */
    String collectionNamePrefix;
}
