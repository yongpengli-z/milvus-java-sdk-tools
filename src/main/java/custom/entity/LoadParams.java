package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Load collection 参数。
 * <p>
 * 对应前端组件：`loadEdit.vue`
 */
@Data
public class LoadParams {
    /**
     * 是否加载实例内全部 collection。
     * <p>
     * 前端：`loadEdit.vue` -> "Load all collections"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean loadAll;

    /**
     * Collection 名称（当 {@link #loadAll}=false 时使用）。
     * <p>
     * 前端：`loadEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String collectionName;

    /**
     * Collection 名称前缀过滤（可选）。
     * <p>
     * 非空时进入多 collection 模式：对目标集合（loadAll=true 时为实例全量列表，
     * 否则为 globalCollectionNames 池子）按前缀过滤后逐个 load。
     * 与 {@link #collectionRangeStart}/{@link #collectionRangeEnd} 可叠加（先前缀、再排序切片）。
     * <p>
     * 前端默认值：""
     */
    private String collectionNamePrefix;

    /**
     * Collection 区间起始下标（可选，默认 -1 不启用）。
     * <p>
     * >=0 时进入区间模式：前缀过滤后按名称排序，再取 [rangeStart, rangeEnd) 切片，
     * 用于多 client 物理分割（如 client0 取 [0,334)、client1 取 [334,668)）。
     */
    private int collectionRangeStart = -1;

    /**
     * Collection 区间结束下标（开区间，可选，默认 -1）。
     * <=0 或超出池子大小表示取到末尾。
     */
    private int collectionRangeEnd = -1;

    /**
     * 需要加载的字段列表（可选）。
     * <p>
     * 前端：`loadEdit.vue` -> "LoadFields"（逗号分隔输入）
     * <p>
     * 为空/空数组表示加载全部字段。
     * <p>
     * 前端默认值：[]（空数组）
     */
    private List<String> loadFields;

    /**
     * 是否跳过加载动态列（Dynamic Field）。
     * <p>
     * 前端：`loadEdit.vue` -> "Skip Load Dynamic Field"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean skipLoadDynamicField;

    /**
     * Load 时的副本数（replica number）。
     * <p>
     * 对应 SDK 的 {@code LoadCollectionReq.numReplicas}。
     * <p>
     * 0 或不传：使用 SDK 默认值（1）。
     * <p>
     * 前端默认值：0
     */
    private int replicaNum;

}
