package custom.entity;

import lombok.Data;

import java.util.List;

/** Parameters for the SDK 3.0.4 scalar-order vector-search capability. */
@Data
public class SearchOrderByParams {
    private String collectionName;
    private String annsField;
    private int nq;
    private int topK;
    private List<String> outputFields;
    private String filter;
    private List<String> partitionNames;
    private List<OrderByFieldParams> orderByFields;
    private long timeout;
    private String targetEndpoint;

    @Data
    public static class OrderByFieldParams {
        private String fieldName;
        /** ASC or DESC. */
        private String direction;
    }
}
