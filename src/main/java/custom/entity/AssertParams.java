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
     * Stop running subsequent components if this assert component fails.
     * Assertions inside this component are always all executed.
     */
    private boolean failFast;

    /**
     * Target endpoint used by all assertions in this component.
     */
    private String targetEndpoint;

    /**
     * Collection used by all assertions in this component.
     */
    private String collectionName;
    private String collectionRule;

    /**
     * Assertion definitions. Frontend can add/remove items from this list.
     */
    private List<AssertionItem> assertions;

    @Data
    public static class AssertionItem {
        /**
         * Assertion type: query, search, or describeIndex.
         */
        private String type;

        /**
         * Metric to compare.
         * <ul>
         *   <li>query: returnCount, count</li>
         *   <li>search: returnCount, totalReturnCount</li>
         *   <li>describeIndex: indexedRows, totalRows, pendingIndexRows</li>
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

        private String filter;
        private List<String> outputs;
        private List<String> partitionNames;
        private List<GeneralDataRole> generalFilterRoleList;

        private QueryAssertion query;
        private SearchAssertion search;
        private DescribeIndexAssertion describeIndex;

        public void setIds(List<Object> ids) {
            ensureQuery().setIds(ids);
        }

        public void setLimit(long limit) {
            ensureQuery().setLimit(limit);
        }

        public void setOffset(long offset) {
            ensureQuery().setOffset(offset);
        }

        public void setAnnsField(String annsField) {
            ensureSearch().setAnnsField(annsField);
        }

        public void setNq(int nq) {
            ensureSearch().setNq(nq);
        }

        public void setTopK(int topK) {
            ensureSearch().setTopK(topK);
        }

        public void setSearchLevel(int searchLevel) {
            ensureSearch().setSearchLevel(searchLevel);
        }

        public void setIndexAlgo(String indexAlgo) {
            ensureSearch().setIndexAlgo(indexAlgo);
        }

        public void setTimeout(long timeout) {
            ensureSearch().setTimeout(timeout);
        }

        public void setVectorSampleSize(int vectorSampleSize) {
            ensureSearch().setVectorSampleSize(vectorSampleSize);
        }

        public void setFieldName(String fieldName) {
            ensureDescribeIndex().setFieldName(fieldName);
        }

        public void setIndexName(String indexName) {
            ensureDescribeIndex().setIndexName(indexName);
        }

        public void setDatabaseName(String databaseName) {
            ensureDescribeIndex().setDatabaseName(databaseName);
        }

        public void setCollectionName(String collectionName) {
            // Legacy per-assertion field. Collection is now configured on AssertParams.
        }

        public void setCollectionRule(String collectionRule) {
            // Legacy per-assertion field. Collection is now configured on AssertParams.
        }

        private QueryAssertion ensureQuery() {
            if (query == null) {
                query = new QueryAssertion();
            }
            return query;
        }

        private SearchAssertion ensureSearch() {
            if (search == null) {
                search = new SearchAssertion();
            }
            return search;
        }

        private DescribeIndexAssertion ensureDescribeIndex() {
            if (describeIndex == null) {
                describeIndex = new DescribeIndexAssertion();
            }
            return describeIndex;
        }
    }

    @Data
    public static class QueryAssertion {
        private List<Object> ids;
        private long limit;
        private long offset;
    }

    @Data
    public static class SearchAssertion {
        private String annsField;
        private int nq;
        private int topK;
        private int searchLevel;
        private String indexAlgo;
        private long timeout;
        private int vectorSampleSize;
    }

    @Data
    public static class DescribeIndexAssertion {
        private String fieldName;
        private String indexName;
        private String databaseName;
    }
}
