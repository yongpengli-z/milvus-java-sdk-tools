package custom.entity;

import io.milvus.v2.common.DataType;
import lombok.Data;

@Data
public class FieldParams {
    String fieldName;
    DataType dataType;
    boolean isPrimaryKey;
    int dim;
    int maxLength;
    int maxCapacity;
    DataType elementType;
    boolean isPartitionKey;
    boolean isNullable;
    boolean isAutoId;
}
