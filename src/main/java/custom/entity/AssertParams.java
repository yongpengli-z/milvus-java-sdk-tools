package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

/**
 * One-shot business assertions.
 * <p>
 * The component runs each assertion once. It is intended for correctness
 * checks, not for performance or long-running workload generation.
 */
@Data
public class AssertParams {
    /**
     * Stop running remaining assertions after the first failed assertion.
     */
    private boolean failFast;

    /**
     * Default target endpoint for assertions that do not set their own.
     */
    private String targetEndpoint;

    /**
     * Assertion definitions. Frontend can add/remove items from this list.
     */
    private List<AssertionItem> assertions;

    @Data
    public static class AssertionItem {
        /**
         * Optional display name.
         */
        private String name;

        /**
         * Assertion type: query or search.
         */
        private String type;

        /**
         * Metric to compare.
         * <ul>
         *   <li>query: returnCount, count</li>
         *   <li>search: returnCount, totalReturnCount</li>
         * </ul>
         */
        private String metric;

        /**
         * Comparison operator: eq, ne, gt, gte, lt, lte, between.
         */
        private String operator;

        /**
         * Expected value. For between, use a two-item list: [min, max].
         */
        private Object expected;

        private String collectionName;
        private String collectionRule;
        private String targetEndpoint;
        private String filter;
        private List<String> outputs;
        private List<Object> ids;
        private List<String> partitionNames;
        private long limit;
        private long offset;
        private List<GeneralDataRole> generalFilterRoleList;

        private String annsField;
        private int nq;
        private int topK;
        private int searchLevel;
        private String indexAlgo;
        private long timeout;
        private int vectorSampleSize;
    }
}
