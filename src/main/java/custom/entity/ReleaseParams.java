package custom.entity;

import lombok.Data;

/**
 * Release 参数（释放 collection 的内存加载）。
 * <p>
 * 对应前端组件：`releaseEdit.vue`
 */
@Data
public class ReleaseParams {
    /**
     * 是否 release 实例内全部 collection。
     * <p>
     * 前端：`releaseEdit.vue` -> "Release all collections"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean releaseAll;

    /**
     * Collection 名称（当 {@link #releaseAll}=false 时使用）。
     * <p>
     * 前端：`releaseEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String collectionName;
}
