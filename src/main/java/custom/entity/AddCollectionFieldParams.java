package custom.entity;


import io.milvus.v2.common.DataType;
import lombok.Data;

import java.util.List;


@Data
public class AddCollectionFieldParams {
    private String collectionName;
    private String databaseName;
    private String fieldName;
    private DataType dataType;
    private Integer maxLength;
    private Boolean isPrimaryKey;
    private Boolean isPartitionKey;
    private Boolean isClusteringKey;
    private Boolean autoID;
    private Integer dimension;
    private DataType elementType;
    private Integer maxCapacity;
    private Boolean isNullable;
    private Object defaultValue;
    private boolean enableDefaultValue;
    private Boolean enableAnalyzer;
    private Boolean enableMatch;
    private List<AnalyzerParams> analyzerParamsList;

    @Data
    public static class AnalyzerParams{
        String paramsKey;
        Object paramsValue;
    }
}
