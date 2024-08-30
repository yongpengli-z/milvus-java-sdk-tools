package custom.entity;

import io.milvus.v2.common.DataType;
import lombok.Data;

@Data
public class VectorInfo {
    private String fieldName;
    private DataType dataType;
    private int dim;
}
