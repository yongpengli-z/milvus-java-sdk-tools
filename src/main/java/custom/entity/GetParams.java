package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Get 参数（按 ID 获取实体）。
 * <p>
 * 对应前端组件：`getEdit.vue`
 */
@Data
public class GetParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`getEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`getEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String databaseName;

    /**
     * 要获取的实体 ID 列表。
     * <p>
     * 前端：`getEdit.vue` -> "IDs"
     * <p>
     * 前端必填：是
     */
    private List<Object> ids;

    /**
     * 输出字段列表（outputFields）。
     * <p>
     * 前端：`getEdit.vue` -> "Output Fields"
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> outputFields;

    /**
     * Partition 名称列表（可选）。
     * <p>
     * 前端：`getEdit.vue` -> "Partition Names"
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> partitionNames;
}
