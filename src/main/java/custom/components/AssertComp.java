package custom.components;

import custom.common.CommonFunction;
import custom.entity.AssertParams;
import custom.entity.result.AssertResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.RetryConfig;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static custom.BaseTest.*;

@Slf4j
public class AssertComp {
    public static AssertResult assertOnce(AssertParams assertParams) {
        List<AssertResult.AssertItemResult> itemResults = new ArrayList<>();
        List<String> assertMessages = new ArrayList<>();

        if (assertParams == null || assertParams.getAssertions() == null || assertParams.getAssertions().isEmpty()) {
            CommonResult commonResult = CommonResult.builder()
                    .result(ResultEnum.FAIL.result)
                    .message("AssertParams.assertions is empty")
                    .build();
            assertMessages.add("[ASSERT FAIL] AssertParams.assertions is empty");
            return AssertResult.builder()
                    .commonResult(commonResult)
                    .totalAssertions(0)
                    .passedAssertions(0)
                    .failedAssertions(0)
                    .assertionResults(itemResults)
                    .assertMessages(assertMessages)
                    .build();
        }

        for (AssertParams.AssertionItem assertion : assertParams.getAssertions()) {
            AssertResult.AssertItemResult itemResult = runAssertion(assertParams, assertion);
            itemResults.add(itemResult);
            if (!itemResult.isPassed()) {
                assertMessages.add("[ASSERT FAIL] " + itemResult.getMessage());
                if (assertParams.isFailFast()) {
                    break;
                }
            }
        }

        int passed = (int) itemResults.stream().filter(AssertResult.AssertItemResult::isPassed).count();
        int failed = itemResults.size() - passed;
        CommonResult commonResult = CommonResult.builder()
                .result(failed > 0 ? ResultEnum.FAIL.result : ResultEnum.SUCCESS.result)
                .message(failed > 0 ? failed + " assertion(s) failed" : null)
                .build();

        return AssertResult.builder()
                .commonResult(commonResult)
                .totalAssertions(assertParams.getAssertions().size())
                .passedAssertions(passed)
                .failedAssertions(failed)
                .assertionResults(itemResults)
                .assertMessages(assertMessages)
                .build();
    }

    private static AssertResult.AssertItemResult runAssertion(AssertParams assertParams, AssertParams.AssertionItem assertion) {
        String type = normalize(assertion.getType());
        String metric = normalize(assertion.getMetric());
        String operator = normalize(assertion.getOperator());
        if (isBlank(operator)) {
            operator = "eq";
        }
        try {
            MetricValue metricValue;
            if ("query".equals(type)) {
                metricValue = queryMetric(assertParams, assertion, metric);
            } else if ("search".equals(type)) {
                metricValue = searchMetric(assertParams, assertion, metric);
            } else {
                throw new IllegalArgumentException("unsupported assertion type: " + assertion.getType());
            }

            boolean passed = compare(metricValue.actual, assertion.getExpected(), operator);
            String message = buildMessage(assertion, metricValue.actual, operator, passed);
            return AssertResult.AssertItemResult.builder()
                    .type(type)
                    .metric(metric)
                    .operator(operator)
                    .expected(assertion.getExpected())
                    .actual(metricValue.actual)
                    .passed(passed)
                    .message(message)
                    .details(metricValue.details)
                    .build();
        } catch (Exception e) {
            String message = buildAssertionName(assertion) + " exception: " + e.getMessage();
            log.error("Assert item failed with exception: {}", message, e);
            return AssertResult.AssertItemResult.builder()
                    .type(type)
                    .metric(metric)
                    .operator(operator)
                    .expected(assertion.getExpected())
                    .passed(false)
                    .message(message)
                    .build();
        }
    }

    private static MetricValue queryMetric(AssertParams assertParams, AssertParams.AssertionItem assertion, String metric) {
        MilvusClientV2 client = getMilvusClient(resolveTargetEndpoint(assertParams));
        AssertParams.QueryAssertion query = queryParams(assertion);
        String collectionName = resolveCollectionName(assertion.getCollectionName(), assertion.getCollectionRule());
        String filter = resolveFilter(assertion.getFilter(), assertion.getGeneralFilterRoleList());
        List<String> outputs = normalizeOutputs(assertion.getOutputs());
        if ("count".equals(metric) && outputs.isEmpty()) {
            outputs.add("count(*)");
        }

        QueryReq queryReq = QueryReq.builder()
                .collectionName(collectionName)
                .outputFields(outputs)
                .ids(query.getIds() == null || query.getIds().isEmpty() ? null : query.getIds())
                .filter(isBlank(filter) ? null : filter)
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .partitionNames(assertion.getPartitionNames() == null ? new ArrayList<>() : assertion.getPartitionNames())
                .offset(query.getOffset())
                .build();
        if (query.getLimit() > 0) {
            queryReq.setLimit(query.getLimit());
        }

        long startTime = System.currentTimeMillis();
        QueryResp queryResp = client.query(queryReq);
        long costMillis = System.currentTimeMillis() - startTime;
        int returnCount = queryResp.getQueryResults() == null ? 0 : queryResp.getQueryResults().size();

        Map<String, Object> details = new HashMap<>();
        details.put("collectionName", collectionName);
        details.put("filter", filter);
        details.put("outputs", outputs);
        details.put("returnCount", returnCount);
        details.put("costMillis", costMillis);

        Object actual;
        if ("returncount".equals(metric)) {
            actual = returnCount;
        } else if ("count".equals(metric)) {
            actual = extractCount(queryResp, outputs);
            details.put("count", actual);
        } else {
            throw new IllegalArgumentException("unsupported query metric: " + metric);
        }
        return new MetricValue(actual, details);
    }

    private static MetricValue searchMetric(AssertParams assertParams, AssertParams.AssertionItem assertion, String metric) {
        MilvusClientV2 client = getMilvusClient(resolveTargetEndpoint(assertParams));
        AssertParams.SearchAssertion search = searchParams(assertion);
        String collectionName = resolveCollectionName(assertion.getCollectionName(), assertion.getCollectionRule());
        String filter = resolveFilter(assertion.getFilter(), assertion.getGeneralFilterRoleList());
        int nq = search.getNq() > 0 ? search.getNq() : 1;
        int topK = search.getTopK() > 0 ? search.getTopK() : 1;
        int sampleSize = search.getVectorSampleSize() > 0 ? search.getVectorSampleSize() : Math.max(1000, nq);
        String annsField = search.getAnnsField();
        if (isBlank(annsField)) {
            throw new IllegalArgumentException("search assertion requires annsField");
        }

        DescribeCollectionResp describeCollectionResp = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(collectionName).build());
        List<BaseVector> searchBaseVectors = providerSearchVectors(client, collectionName, annsField,
                sampleSize, describeCollectionResp);
        if (searchBaseVectors == null || searchBaseVectors.isEmpty()) {
            throw new IllegalStateException("no base vectors found for search assertion");
        }
        List<BaseVector> baseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, nq);
        if (baseVectors.isEmpty()) {
            throw new IllegalStateException("no query vectors generated for search assertion");
        }

        Map<String, Object> searchLevel = new HashMap<>();
        searchLevel.put("level", search.getSearchLevel() == 0 ? 1 : search.getSearchLevel());
        if (!isBlank(search.getIndexAlgo())) {
            searchLevel.put("index_algo", search.getIndexAlgo());
        }

        SearchReq searchReq = SearchReq.builder()
                .topK(topK)
                .outputFields(normalizeOutputs(assertion.getOutputs()))
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .collectionName(collectionName)
                .searchParams(searchLevel)
                .filter(isBlank(filter) ? null : filter)
                .data(baseVectors)
                .annsField(annsField)
                .partitionNames(assertion.getPartitionNames() == null ? new ArrayList<>() : assertion.getPartitionNames())
                .build();

        long timeoutMs = search.getTimeout() > 0 ? search.getTimeout() : 800;
        long startTime = System.currentTimeMillis();
        SearchResp searchResp = client.withTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withRetry(RetryConfig.builder().maxRetryTimes(1).build())
                .search(searchReq);
        long costMillis = System.currentTimeMillis() - startTime;

        List<Integer> resultCounts = new ArrayList<>();
        if (searchResp.getSearchResults() != null) {
            for (List<SearchResp.SearchResult> results : searchResp.getSearchResults()) {
                resultCounts.add(results == null ? 0 : results.size());
            }
        }
        int returnCount = resultCounts.isEmpty() ? 0 : resultCounts.get(0);
        int totalReturnCount = resultCounts.stream().mapToInt(Integer::intValue).sum();

        Map<String, Object> details = new HashMap<>();
        details.put("collectionName", collectionName);
        details.put("filter", filter);
        details.put("annsField", annsField);
        details.put("nq", nq);
        details.put("topK", topK);
        details.put("resultCounts", resultCounts);
        details.put("returnCount", returnCount);
        details.put("totalReturnCount", totalReturnCount);
        details.put("costMillis", costMillis);

        Object actual;
        if ("returncount".equals(metric)) {
            actual = returnCount;
        } else if ("totalreturncount".equals(metric)) {
            actual = totalReturnCount;
        } else {
            throw new IllegalArgumentException("unsupported search metric: " + metric);
        }
        return new MetricValue(actual, details);
    }

    private static List<BaseVector> providerSearchVectors(MilvusClientV2 client, String collectionName, String annsField,
                                                          int sampleSize, DescribeCollectionResp describeCollectionResp) {
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.Function> functionList = collectionSchema.getFunctionList();
        if (functionList != null) {
            for (CreateCollectionReq.Function function : functionList) {
                if (function.getOutputFieldNames() != null && function.getOutputFieldNames().contains(annsField)) {
                    int index = function.getOutputFieldNames().indexOf(annsField);
                    String inputFieldName = function.getInputFieldNames().get(index);
                    log.info("Assert search uses function input field: {}", inputFieldName);
                    return CommonFunction.providerSearchFunctionData(client, collectionName, sampleSize, inputFieldName);
                }
            }
        }
        return CommonFunction.providerSearchVectorDataset(client, collectionName, sampleSize, annsField);
    }

    private static Object extractCount(QueryResp queryResp, List<String> outputs) {
        if (queryResp == null || queryResp.getQueryResults() == null || queryResp.getQueryResults().isEmpty()) {
            throw new IllegalStateException("query count result is empty");
        }
        Map<String, Object> entity = queryResp.getQueryResults().get(0).getEntity();
        if (entity == null || entity.isEmpty()) {
            throw new IllegalStateException("query count entity is empty");
        }
        for (String output : outputs) {
            if (entity.containsKey(output) && entity.get(output) instanceof Number) {
                return entity.get(output);
            }
        }
        for (Map.Entry<String, Object> entry : entity.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
            if (key.contains("count") && entry.getValue() instanceof Number) {
                return entry.getValue();
            }
        }
        if (entity.size() == 1) {
            Object onlyValue = entity.values().iterator().next();
            if (onlyValue instanceof Number) {
                return onlyValue;
            }
        }
        throw new IllegalStateException("cannot find numeric count field in query entity: " + entity);
    }

    private static boolean compare(Object actual, Object expected, String operator) {
        String op = isBlank(operator) ? "eq" : operator;
        if ("between".equals(op)) {
            List<?> range = parseRange(expected);
            BigDecimal actualNumber = toBigDecimal(actual, "actual");
            BigDecimal min = toBigDecimal(range.get(0), "expected[0]");
            BigDecimal max = toBigDecimal(range.get(1), "expected[1]");
            return actualNumber.compareTo(min) >= 0 && actualNumber.compareTo(max) <= 0;
        }

        if ("eq".equals(op)) {
            return compareEquals(actual, expected);
        }
        if ("ne".equals(op)) {
            return !compareEquals(actual, expected);
        }

        BigDecimal actualNumber = toBigDecimal(actual, "actual");
        BigDecimal expectedNumber = toBigDecimal(expected, "expected");
        int compare = actualNumber.compareTo(expectedNumber);
        if ("gt".equals(op)) {
            return compare > 0;
        }
        if ("gte".equals(op)) {
            return compare >= 0;
        }
        if ("lt".equals(op)) {
            return compare < 0;
        }
        if ("lte".equals(op)) {
            return compare <= 0;
        }
        throw new IllegalArgumentException("unsupported operator: " + operator);
    }

    private static boolean compareEquals(Object actual, Object expected) {
        if (actual instanceof Number || expected instanceof Number) {
            return toBigDecimal(actual, "actual").compareTo(toBigDecimal(expected, "expected")) == 0;
        }
        return Objects.equals(actual, expected);
    }

    private static List<?> parseRange(Object expected) {
        if (expected instanceof List && ((List<?>) expected).size() == 2) {
            return (List<?>) expected;
        }
        throw new IllegalArgumentException("between operator requires expected as [min, max]");
    }

    private static BigDecimal toBigDecimal(Object value, String fieldName) {
        if (value instanceof Number || value instanceof String) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(fieldName + " is not numeric: " + value);
            }
        }
        throw new IllegalArgumentException(fieldName + " is not numeric: " + value);
    }

    private static String buildMessage(AssertParams.AssertionItem assertion, Object actual, String operator, boolean passed) {
        return buildAssertionName(assertion)
                + " actual=" + actual
                + " " + operator
                + " expected=" + assertion.getExpected()
                + " => " + (passed ? "pass" : "fail");
    }

    private static String buildAssertionName(AssertParams.AssertionItem assertion) {
        return normalize(assertion.getType()) + "." + normalize(assertion.getMetric());
    }

    private static String resolveTargetEndpoint(AssertParams assertParams) {
        return assertParams == null ? "" : assertParams.getTargetEndpoint();
    }

    private static AssertParams.QueryAssertion queryParams(AssertParams.AssertionItem assertion) {
        return assertion.getQuery() == null ? new AssertParams.QueryAssertion() : assertion.getQuery();
    }

    private static AssertParams.SearchAssertion searchParams(AssertParams.AssertionItem assertion) {
        return assertion.getSearch() == null ? new AssertParams.SearchAssertion() : assertion.getSearch();
    }

    private static String resolveCollectionName(String collectionName, String collectionRule) {
        if (isBlank(collectionRule)) {
            return !isBlank(collectionName) ? collectionName : globalCollectionNames.get(globalCollectionNames.size() - 1);
        }
        if ("random".equalsIgnoreCase(collectionRule)) {
            return globalCollectionNames.get(new java.util.Random().nextInt(globalCollectionNames.size()));
        }
        if ("sequence".equalsIgnoreCase(collectionRule)) {
            String resolved = globalCollectionNames.get(queryCollectionIndex);
            queryCollectionIndex += 1;
            queryCollectionIndex = queryCollectionIndex % globalCollectionNames.size();
            return resolved;
        }
        return !isBlank(collectionName) ? collectionName : globalCollectionNames.get(globalCollectionNames.size() - 1);
    }

    private static String resolveFilter(String filter, List<GeneralDataRole> generalFilterRoleList) {
        if (generalFilterRoleList == null || generalFilterRoleList.isEmpty()) {
            return filter;
        }
        List<GeneralDataRole> roleList = generalFilterRoleList.stream()
                .filter(role -> role.getFieldName() != null && !role.getFieldName().equalsIgnoreCase(""))
                .collect(Collectors.toList());
        for (GeneralDataRole role : roleList) {
            List<RandomRangeParams> randomRangeParamsList = role.getRandomRangeParamsList();
            if (randomRangeParamsList == null || randomRangeParamsList.isEmpty()) {
                continue;
            }
            randomRangeParamsList.sort(Comparator.comparing(RandomRangeParams::getStart));
            int replaceValue;
            if ("sequence".equalsIgnoreCase(role.getSequenceOrRandom())) {
                replaceValue = CommonFunction.advanceSequenceForSearch(randomRangeParamsList, 1, 0, 0);
            } else {
                replaceValue = CommonFunction.advanceRandom(randomRangeParamsList);
            }
            filter = CommonFunction.replaceFilterPlaceholder(filter, role, replaceValue);
        }
        return filter;
    }

    private static List<String> normalizeOutputs(List<String> outputs) {
        List<String> normalized = new ArrayList<>();
        if (outputs == null) {
            return normalized;
        }
        for (String output : outputs) {
            if (!isBlank(output)) {
                normalized.add(output);
            }
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class MetricValue {
        private final Object actual;
        private final Map<String, Object> details;

        private MetricValue(Object actual, Map<String, Object> details) {
            this.actual = actual;
            this.details = details;
        }
    }
}
