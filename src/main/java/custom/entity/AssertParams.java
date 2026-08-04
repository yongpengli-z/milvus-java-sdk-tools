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
     * Target endpoint used by all assertions in this component.
     */
    private String targetEndpoint;

    /**
     * Assertion definitions. Frontend can add/remove items from this list.
     */
    private List<AssertionItem> assertions;

    @Data
    public static class AssertionItem {
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
        private String filter;
        private List<String> outputs;
        private List<String> partitionNames;
        private List<GeneralDataRole> generalFilterRoleList;

        private QueryAssertion query;
        private SearchAssertion search;

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
}
