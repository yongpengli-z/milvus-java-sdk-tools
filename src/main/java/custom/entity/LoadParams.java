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

}
