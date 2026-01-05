package custom.entity;

import io.milvus.v2.common.DataType;
import lombok.Data;

import java.util.List;

/**
 * Collection 字段 schema 参数。
 * <p>
 * 对应前端组件：`createCollectionEdit.vue` -> Fields 表格行。
 */
@Data
public class FieldParams {
    /**
     * 字段名（FieldName）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "FieldName"
     * <p>
     * 前端默认值：初始模板包含 `Int64_0`（PK）和 `FloatVector_1`（向量）；新增字段行默认 ""（空字符串）。
     */
    String fieldName;

    /**
     * 字段类型（DataType，Milvus 枚举名）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "DataType"
     * <p>
     * 注意：必须是 `io.milvus.v2.common.DataType` 的枚举常量名（例如 `Int64` / `VarChar` / `FloatVector` 等）。
     * <p>
     * 前端默认值：初始模板为 `Int64` / `FloatVector`；新增字段行默认 ""（空字符串，占位）。
     * <p>
     * 建议：生成 JSON 时 dataType 不能为空字符串，应传具体枚举名或不传。
     */
    DataType dataType;

    /**
     * 是否主键（PK）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "IsPrimaryKey"
     * <p>
     * 约束：主键字段通常不可为空；且一个 collection 只能有一个主键。
     * <p>
     * 前端默认值：初始模板首字段为 true；新增字段行默认 false。
     */
    boolean isPrimaryKey;

    /**
     * 向量维度（仅 Vector 类型生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Dim"
     * <p>
     * 前端默认值：初始向量字段为 768；新增字段行默认 null（占位）。
     * <p>
     * 注意：后端字段类型为 int，JSON 传 null/不传时会按 0 处理。
     */
    int dim;

    /**
     * VarChar/String 最大长度（仅 `VarChar`/`String` 或 `Array(elementType=VarChar)` 生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "MaxLength"
     * <p>
     * 前端默认值：0（模板）/ null（新增字段行占位）
     * <p>
     * 注意：后端字段类型为 int，JSON 传 null/不传时会按 0 处理。
     */
    int maxLength;

    /**
     * Array 最大容量（仅 `Array` 生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "MaxCapacity"
     * <p>
     * 前端默认值：0（模板）/ null（新增字段行占位）
     * <p>
     * 注意：后端字段类型为 int，JSON 传 null/不传时会按 0 处理。
     */
    int maxCapacity;

    /**
     * Array 元素类型（仅 `Array` 生效；为 `io.milvus.v2.common.DataType` 枚举名）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "ElementType"
     * <p>
     * 前端默认值：null（新增字段行）；模板里可能为 0（占位）。
     * <p>
     * 建议：生成 JSON 时 elementType 应为 DataType 枚举名或 null。
     * <p>
     * 注意：当 elementType 为 DataType.STRUCT 时，需要同时设置 {@link #structSchema}。
     */
    DataType elementType;

    /**
     * Struct Schema（仅当 `dataType=DataType.Array` 且 `elementType=DataType.STRUCT` 时生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "StructSchema"
     * <p>
     * 用于定义 Array of Struct 中 Struct 的子字段列表。
     * <p>
     * 前端默认值：null 或空列表
     * <p>
     * 注意：
     * - Struct 只能作为 Array 的元素类型使用
     * - Struct 子字段不能是 Struct、Array、Json
     * - Struct 可以包含向量字段，从而实现 Array of Vector
     */
    List<StructFieldParams> structSchema;

    /**
     * 是否 Partition Key。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "IsPartitionKey"
     * <p>
     * 前端默认值：false
     */
    boolean isPartitionKey;

    /**
     * 是否允许为 NULL。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "IsNullable"
     * <p>
     * 前端默认值：false
     */
    boolean isNullable;

    /**
     * 主键是否 AutoID（仅当 {@link #isPrimaryKey}=true 时生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "isAutoId"
     * <p>
     * 前端默认值：false
     */
    boolean isAutoId;

    /**
     * EnableMatch（主要用于文本/匹配相关能力；影响数据生成策略）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "EnableMatch"
     * <p>
     * 前端默认值：false
     */
    boolean enableMatch;

    /**
     * 是否开启 Analyzer（分词器/分析器）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "EnableAnalyzer"
     * <p>
     * 前端默认值：false
     */
    boolean enableAnalyzer;

    /**
     * Analyzer 参数列表（仅当 {@link #enableAnalyzer}=true 时生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "AnalyzerParamsList"
     * <p>
     * 前端默认值：`[{paramsKey:"", paramsValue:""}]`（新增字段行默认带 1 条空参数）
     */
    List<AnalyzerParams> analyzerParamsList;

    @Data
    public static class AnalyzerParams{
        /**
         * Analyzer 参数 Key。
         * <p>
         * 前端默认值：""（空字符串）
         */
        String paramsKey;

        /**
         * Analyzer 参数 Value（允许为任意 JSON 值）。
         */
        Object paramsValue;
    }
}
