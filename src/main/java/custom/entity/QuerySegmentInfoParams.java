package custom.entity;

import lombok.Data;

/**
 * Query Segment Info 参数（V1 API，查询 query nodes 上的 segment 信息）。
 * <p>
 * 对应前端组件：`querySegmentEdit.vue`
 */
@Data
public class QuerySegmentInfoParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`querySegmentEdit.vue` -> "collectionName"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String collectionName;
}
