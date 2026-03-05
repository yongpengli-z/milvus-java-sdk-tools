package custom.entity;

import lombok.Data;

/**
 * List Partitions 参数。
 * <p>
 * 对应前端组件：`listPartitionsEdit.vue`
 */
@Data
public class ListPartitionsParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`listPartitionsEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;
}
