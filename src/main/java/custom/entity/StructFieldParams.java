package custom.entity;

import io.milvus.v2.common.DataType;
import lombok.Data;

/**
 * Struct 子字段参数。
 * <p>
 * 用于定义 Array of Struct 中 Struct 的子字段。
 * <p>
 * 注意：
 * - Struct 只能作为 DataType.ARRAY 的元素类型使用
 * - Struct 子字段不能是 Struct、Array、Json
 * - Struct 可以包含向量字段，从而实现 Array of Vector
 */
@Data
public class StructFieldParams {
    /**
     * 子字段名。
     * <p>
     * 前端：`createCollectionEdit.vue` -> Struct 子字段的 "FieldName"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String fieldName;

    /**
     * 子字段类型（DataType，Milvus 枚举名）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> Struct 子字段的 "DataType"
     * <p>
     * 注意：必须是 `io.milvus.v2.common.DataType` 的枚举常量名。
     * <p>
     * 限制：不能是 Struct、Array、Json
     * <p>
     * 支持：可以包含向量类型（FloatVector、BinaryVector 等），从而实现 Array of Vector
     */
    DataType dataType;

    /**
     * 向量维度（仅 Vector 类型生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> Struct 子字段的 "Dim"
     * <p>
     * 前端默认值：0（占位）
     * <p>
     * 注意：仅当 dataType 为向量类型时生效
     */
    int dim;

    /**
     * VarChar/String 最大长度（仅 `VarChar`/`String` 生效）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> Struct 子字段的 "MaxLength"
     * <p>
     * 前端默认值：0（占位）
     * <p>
     * 注意：仅当 dataType 为 VarChar 或 String 时生效
     */
    int maxLength;

    /**
     * 是否允许为 NULL。
     * <p>
     * 前端：`createCollectionEdit.vue` -> Struct 子字段的 "IsNullable"
     * <p>
     * 前端默认值：false
     * <p>
     * 注意：根据文档，Struct 暂不支持 nullable 字段，但保留此字段以备将来使用
     */
    boolean isNullable;
}
