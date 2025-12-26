package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Delete（删除数据）参数。
 * <p>
 * 对应前端组件：`deleteEdit.vue`
 */
@Data
public class DeleteParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`deleteEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * 要删除的主键 ID 列表（可选）。
     * <p>
     * 前端：`deleteEdit.vue` -> "IDs"（逗号分隔输入）
     * <p>
     * ids 与 filter 可二选一或同时指定（以 Milvus SDK 行为为准）。
     * <p>
     * 前端默认值：[]（空数组）
     */
    List<Object> ids;

    /**
     * 删除过滤表达式（Milvus expr / filter，可选）。
     * <p>
     * 前端：`deleteEdit.vue` -> "Filter"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String filter;

    /**
     * Partition 名称（可选）。
     * <p>
     * 前端：`deleteEdit.vue` -> "Partition Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String partitionName;
}
