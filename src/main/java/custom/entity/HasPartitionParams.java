package custom.entity;

import lombok.Data;

/**
 * Has Partition 参数。
 * <p>
 * 对应前端组件：`hasPartitionEdit.vue`
 */
@Data
public class HasPartitionParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`hasPartitionEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Partition 名称。
     * <p>
     * 前端：`hasPartitionEdit.vue` -> "Partition Name"
     * <p>
     * 前端必填：是
     */
    private String partitionName;
}
