package custom.entity;

import io.milvus.common.clientenum.FunctionType;
import lombok.Data;

import java.util.List;

@Data
public class FunctionParams {
    FunctionType functionType;
    String name;
    List<String> inputFieldNames;
    List<String> outputFieldNames;
}
