package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Release Partitions 参数。
 * <p>
 * 对应前端组件：`releasePartitionsEdit.vue`
 */
@Data
public class ReleasePartitionsParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`releasePartitionsEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * 要释放的分区名称列表。
     * <p>
     * 前端：`releasePartitionsEdit.vue` -> "Partition Names"（逗号分隔输入）
     * <p>
     * 前端必填：是
     */
    private List<String> partitionNames;
}
