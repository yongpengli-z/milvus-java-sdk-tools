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

    /**
     * Collection 名称前缀过滤（可选）。
     * <p>
     * 非空时进入多 collection 模式：对目标集合（releaseAll=true 时为实例全量列表，
     * 否则为 globalCollectionNames 池子）按前缀过滤后逐个 release。
     * 与 {@link #collectionRangeStart}/{@link #collectionRangeEnd} 可叠加（先前缀、再排序切片）。
     * <p>
     * 前端默认值：""
     */
    private String collectionNamePrefix;

    /**
     * Collection 区间起始下标（可选，默认 -1 不启用）。
     * >=0 时进入区间模式：前缀过滤后按名称排序，再取 [rangeStart, rangeEnd) 切片。
     */
    private int collectionRangeStart = -1;

    /**
     * Collection 区间结束下标（开区间，可选，默认 -1）。
     * <=0 或超出池子大小表示取到末尾。
     */
    private int collectionRangeEnd = -1;
}
