package custom.entity;

import io.milvus.v2.common.DataType;
import lombok.Data;

@Data
public class VectorInfo {
    private String fieldName;
    private DataType dataType;
    private int dim;
    // 用于 Array of Struct 中的向量字段
    private boolean structVector = false;
    private String structFieldName; // struct 字段名（如 "clips"）
    private String structSubFieldName; // struct 子字段名（如 "clip_embedding"）
}
