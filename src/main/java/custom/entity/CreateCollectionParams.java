package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * @Author yongpeng.li @Date 2024/6/4 16:57
 */
@Data
public class CreateCollectionParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端会自动生成随机 collectionName。
     */
    private String collectionName;

    /**
     * Shard 数量。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Shard Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1
     */
    private int shardNum;

    /**
     * 分区数量（numPartitions）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Partitions Num"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：0
     */
    private int numPartitions;

    /**
     * 是否启用动态列（Dynamic Field）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Enable Dynamic"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean enableDynamic;

    /**
     * 字段 schema 列表（至少包含主键 + 向量字段）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Fields"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：2 个字段（PK `Int64_0` + 向量字段 `FloatVector_1`）。
     */
    private List<FieldParams> fieldParamsList;

    /**
     * Function 配置（可选）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Add Function"
     * <p>
     * 示例：BM25（文本 -> 稀疏向量）等。
     * <p>
     * 前端默认值：`{functionType:"", name:"", inputFieldNames:[], outputFieldNames:[]}`（注意 functionType 为空字符串是 UI 占位）。
     * <p>
     * 建议：生成 JSON 时 functionType 为空请使用 `null` 或直接不传，避免 enum 解析失败。
     */
    private FunctionParams functionParams;

    /**
     * Collection properties（可选）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Properties"
     * <p>
     * 前端必填：是（UI 上要求存在该列表，但允许 key/value 为空占位）
     * <p>
     * 前端默认值：`[{propertyKey:"", propertyValue:""}]`
     */
    private List<PropertyM> properties;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String databaseName;

    @Data
    public static class PropertyM {
        /**
         * Property Key（例如 Milvus collection property key）。
         * <p>
         * 前端默认值：""（空字符串）
         */
        String propertyKey;

        /**
         * Property Value（字符串形式）。
         * <p>
         * 前端默认值：""（空字符串）
         */
        String propertyValue;
    }

}
