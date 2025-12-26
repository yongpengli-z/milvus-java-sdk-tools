package custom.entity;

import lombok.Data;

/**
 * Persistent Segment Info 参数（V1 API，查询持久化 segment 信息）。
 * <p>
 * 对应前端组件：`persistentSegmentEdit.vue`
 */
@Data
public class PersistentSegmentInfoParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`persistentSegmentEdit.vue` -> "collectionName"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String collectionName;
}
