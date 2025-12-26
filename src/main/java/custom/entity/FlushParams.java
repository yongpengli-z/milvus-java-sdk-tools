package custom.entity;

import lombok.Data;

/**
 * Flush 参数（将内存数据落盘以保证可见性/持久化）。
 * <p>
 * 对应前端组件：`flushEdit.vue`
 */
@Data
public class FlushParams {
    /**
     * 是否 flush 实例内全部 collection。
     * <p>
     * 前端：`flushEdit.vue` -> "Flush all collections"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean flushAll;

    /**
     * Collection 名称（当 {@link #flushAll}=false 时使用）。
     * <p>
     * 前端：`flushEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String collectionName;
}
