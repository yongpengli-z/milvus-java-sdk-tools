package custom.entity;


import io.milvus.v2.common.DataType;
import lombok.Data;

import java.util.List;


@Data
public class AddCollectionFieldParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    private String collectionName;

    /**
     * Database 名称（可选）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "Database Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String databaseName;

    /**
     * 新增字段名。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "Field Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String fieldName;

    /**
     * 字段类型（Milvus DataType 枚举名）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "DataType"
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 建议：生成 JSON 时 dataType 不能为空字符串，应传具体 DataType 枚举名或不传。
     */
    private DataType dataType;

    /**
     * VarChar/String 最大长度（按 DataType 生效）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "MaxLength"
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 建议：生成 JSON 时 maxLength 传数字或不传（不要传空字符串）。
     */
    private Integer maxLength;

    /**
     * 是否主键（新增字段场景一般不建议设置）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` 参数里包含该字段，但 UI 未展示该开关。
     * <p>
     * 前端默认值：false
     */
    private Boolean isPrimaryKey;

    /**
     * 是否 PartitionKey。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` 参数里包含该字段，但 UI 未展示该开关。
     * <p>
     * 前端默认值：false
     */
    private Boolean isPartitionKey;

    /**
     * 是否 ClusteringKey。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` 参数里包含该字段，但 UI 未展示该开关。
     * <p>
     * 前端默认值：false
     */
    private Boolean isClusteringKey;

    /**
     * 是否 AutoID（主键字段才有意义）。
     * <p>
     * 前端默认值：false
     */
    private Boolean autoID;

    /**
     * 向量维度（Vector 类型生效）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "Dimension"
     * <p>
     * 前端默认值：0
     */
    private Integer dimension;

    /**
     * Array 元素类型（仅 Array 生效）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "ElementType"
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 建议：生成 JSON 时 elementType 应为 DataType 枚举名或 null。
     */
    private DataType elementType;

    /**
     * Array 最大容量（仅 Array 生效）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "MaxCapacity"
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 建议：生成 JSON 时 maxCapacity 传数字或不传（不要传空字符串）。
     */
    private Integer maxCapacity;

    /**
     * 是否可空。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "IsNullable"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：true
     */
    private Boolean isNullable;

    /**
     * 默认值（字符串形式；后端会根据 {@link #dataType} 做类型解析/转换）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "DefaultValue"
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String defaultValue;

    /**
     * 是否启用默认值。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "EnableDefaultValue"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private boolean enableDefaultValue;

    /**
     * 是否启用 Analyzer。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "EnableAnalyzer"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private Boolean enableAnalyzer;

    /**
     * EnableMatch（影响文本/匹配相关能力）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "EnableMatch"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    private Boolean enableMatch;

    /**
     * Analyzer 参数列表（仅当 {@link #enableAnalyzer}=true 时生效）。
     * <p>
     * 前端：`addCollectionFieldEdit.vue` -> "AnalyzerParams"
     * <p>
     * 前端默认值：`[{paramsKey:"", paramsValue:""}]`
     */
    private List<AnalyzerParams> analyzerParamsList;

    @Data
    public static class AnalyzerParams{
        /**
         * Analyzer 参数 Key。
         * <p>
         * 前端默认值：""（空字符串）
         */
        String paramsKey;

        /**
         * Analyzer 参数 Value（允许为任意值）。
         */
        Object paramsValue;
    }
}
