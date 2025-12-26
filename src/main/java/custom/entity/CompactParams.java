package custom.entity;

import lombok.Data;

/**
 * Compact 参数（触发 compaction）。
 * <p>
 * 对应前端组件：`compactEdit.vue`
 */
@Data
public class CompactParams {
    /**
     * 是否对实例内所有 collection 执行 compaction。
     * <p>
     * 前端：`compactEdit.vue` -> "Compact all collections"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    boolean compactAll;

    /**
     * Collection 名称（当 {@link #compactAll}=false 时使用）。
     * <p>
     * 前端：`compactEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String collectionName;

    /**
     * 是否为 clustering compaction（可选）。
     * <p>
     * 前端：`compactEdit.vue` -> "IsClustering"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    boolean isClustering;
}
