package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Load Partitions 参数。
 * <p>
 * 对应前端组件：`loadPartitionsEdit.vue`
 */
@Data
public class LoadPartitionsParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`loadPartitionsEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * 要加载的分区名称列表。
     * <p>
     * 前端：`loadPartitionsEdit.vue` -> "Partition Names"（逗号分隔输入）
     * <p>
     * 前端必填：是
     */
    private List<String> partitionNames;
}
