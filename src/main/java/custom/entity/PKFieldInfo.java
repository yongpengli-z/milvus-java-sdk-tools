package custom.entity;

import io.milvus.v2.common.DataType;
import lombok.Data;

@Data
public class PKFieldInfo {
    private String fieldName;
    private DataType dataType;
}
