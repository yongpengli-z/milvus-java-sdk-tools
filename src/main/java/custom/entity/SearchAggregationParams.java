package custom.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Parameters for the SDK 3.0.4 search-aggregation capability. */
@Data
public class SearchAggregationParams {
    private String collectionName;
    private String annsField;
    private int nq;
    private int topK;
    private List<String> outputFields;
    private String filter;
    private List<String> partitionNames;
    private AggregationParams aggregation;
    private long timeout;
    private String targetEndpoint;

    @Data
    public static class AggregationParams {
        private List<String> fields;
        private long size;
        private Map<String, MetricParams> metrics;
        private List<OrderParams> order;
        private TopHitsParams topHits;
        private AggregationParams subAggregation;
    }

    @Data
    public static class MetricParams {
        /** AVG, SUM, COUNT, MIN, or MAX. */
        private String op;
        private String fieldName;
    }

    @Data
    public static class OrderParams {
        /** A metric alias, _count, or _key. */
        private String key;
        /** ASC or DESC. */
        private String direction;
        private Boolean nullFirst;
    }

    @Data
    public static class TopHitsParams {
        private long size;
        private List<SortParams> sort;
    }

    @Data
    public static class SortParams {
        private String fieldName;
        /** ASC or DESC. */
        private String direction;
        private Boolean nullFirst;
    }
}
