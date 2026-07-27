package custom.components;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.entity.QueryClusterMetricsParams;
import custom.entity.result.CommonResult;
import custom.entity.result.QueryClusterMetricsResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudServiceUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class QueryClusterMetricsComp {
    private static final String DEFAULT_BASE_URL = "https://api.cloud.zilliz.com";

    private QueryClusterMetricsComp() {
    }

    public static QueryClusterMetricsResult query(QueryClusterMetricsParams params) {
        long startMillis = System.currentTimeMillis();
        try {
            validate(params);
            String clusterId = firstText(params.getClusterId(),
                    newInstanceInfo == null ? null : newInstanceInfo.getInstanceId());
            String baseUrl = trimTrailingSlash(firstText(params.getBaseUrl(), DEFAULT_BASE_URL));
            String url = baseUrl + "/v2/clusters/" + clusterId + "/metrics/query";
            JSONObject requestBody = buildRequestBody(params);
            Map<String, String> headers = buildHeaders(params);

            log.info("QueryClusterMetrics request url={}, body={}", url, requestBody.toJSONString());
            HttpResponseBody responseBody = doPostJson(url, headers, requestBody.toJSONString(), params.getSocketTimeout());
            JSONObject responseJson = parseResponse(responseBody.body);
            if (params.isLogResponse()) {
                log.info("QueryClusterMetrics response httpStatus={}, body={}", responseBody.statusCode, responseBody.body);
            }

            QueryClusterMetricsResult result = buildResult(params, clusterId, url, responseBody, responseJson,
                    System.currentTimeMillis() - startMillis);
            log.info("QueryClusterMetrics result: {}", JSONObject.toJSONString(result));
            return result;
        } catch (IllegalArgumentException e) {
            return failure(params, ResultEnum.FAIL.result, e.getMessage(), System.currentTimeMillis() - startMillis);
        } catch (Exception e) {
            log.error("QueryClusterMetrics failed", e);
            return failure(params, ResultEnum.EXCEPTION.result, e.getMessage(), System.currentTimeMillis() - startMillis);
        }
    }

    private static void validate(QueryClusterMetricsParams params) {
        if (params == null) {
            throw new IllegalArgumentException("QueryClusterMetricsParams must not be null");
        }
        String clusterId = firstText(params.getClusterId(),
                newInstanceInfo == null ? null : newInstanceInfo.getInstanceId());
        requireText(clusterId, "clusterId");
        requireText(params.getGranularity(), "granularity");
        if (!hasText(params.getPeriod()) && (!hasText(params.getStart()) || !hasText(params.getEnd()))) {
            throw new IllegalArgumentException("period or both start/end must be set");
        }
        if (params.getMetricQueries() == null || params.getMetricQueries().isEmpty()) {
            if (params.getMetricNames() == null || params.getMetricNames().isEmpty()) {
                throw new IllegalArgumentException("metricQueries or metricNames must be set");
            }
        }
    }

    private static JSONObject buildRequestBody(QueryClusterMetricsParams params) {
        JSONObject body = new JSONObject(true);
        putIfText(body, "start", params.getStart());
        putIfText(body, "end", params.getEnd());
        putIfText(body, "period", params.getPeriod());
        body.put("granularity", params.getGranularity());
        putIfText(body, "dbName", params.getDbName());
        putIfText(body, "collectionName", params.getCollectionName());

        JSONArray metricQueries = new JSONArray();
        if (params.getMetricQueries() != null && !params.getMetricQueries().isEmpty()) {
            for (QueryClusterMetricsParams.MetricQuery metricQuery : params.getMetricQueries()) {
                if (metricQuery == null || !hasText(metricQuery.getName())) {
                    throw new IllegalArgumentException("metricQueries[].name must not be empty");
                }
                JSONObject item = new JSONObject(true);
                item.put("name", metricQuery.getName());
                if (metricQuery.getParams() != null && !metricQuery.getParams().isEmpty()) {
                    item.putAll(metricQuery.getParams());
                }
                metricQueries.add(item);
            }
        } else {
            for (String metricName : params.getMetricNames()) {
                requireText(metricName, "metricNames[]");
                JSONObject item = new JSONObject(true);
                item.put("name", metricName);
                metricQueries.add(item);
            }
        }
        body.put("metricQueries", metricQueries);
        return body;
    }

    private static Map<String, String> buildHeaders(QueryClusterMetricsParams params) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        String token = firstText(params.getApiKey(),
                System.getProperty(firstText(params.getApiKeySystemProperty(), "zilliz.apiKey")),
                System.getenv("ZILLIZ_API_KEY"),
                resolveManagedApiKey(params),
                newInstanceInfo == null ? null : newInstanceInfo.getToken(),
                cloudServiceUserInfo == null ? null : cloudServiceUserInfo.getToken());
        if (hasText(token)) {
            headers.put("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
        }
        if (params.getHeaders() != null) {
            headers.putAll(params.getHeaders());
        }
        return headers;
    }

    private static String resolveManagedApiKey(QueryClusterMetricsParams params) {
        try {
            ensureCloudServiceLogin(params);
            String resp = CloudServiceUtils.listManagedApiKeys();
            JSONObject data = getData(JSONObject.parseObject(resp));
            JSONArray keys = data == null ? null : data.getJSONArray("keys");
            if (keys == null && data != null) {
                keys = data.getJSONArray("Keys");
            }
            if (keys == null) {
                log.warn("QueryClusterMetrics auto api key failed: response does not contain keys");
                return "";
            }
            for (int i = 0; i < keys.size(); i++) {
                JSONObject key = keys.getJSONObject(i);
                Integer type = getInteger(key, "type", "Type");
                String personalKey = getString(key, "key", "Key");
                if (type != null && type == 1 && hasText(personalKey)) {
                    log.info("QueryClusterMetrics auto api key resolved from current account");
                    return personalKey;
                }
            }
            log.warn("QueryClusterMetrics auto api key failed: no type=1 managed key found");
        } catch (Exception e) {
            log.warn("QueryClusterMetrics auto api key failed: {}", e.getMessage());
        }
        return "";
    }

    private static void ensureCloudServiceLogin(QueryClusterMetricsParams params) {
        if (params != null && hasText(params.getAccountEmail())) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(
                    params.getAccountEmail(), params.getAccountPassword());
            return;
        }
        cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
    }

    private static QueryClusterMetricsResult buildResult(QueryClusterMetricsParams params, String clusterId, String url,
                                                        HttpResponseBody responseBody, JSONObject responseJson,
                                                        long costTimeMs) {
        int responseCode = responseJson == null ? -1 : responseJson.getIntValue("code");
        List<QueryClusterMetricsResult.MetricSummary> metricSummaries = summarize(responseJson);
        int metricCount = metricSummaries.size();
        int dataPointCount = metricSummaries.stream().mapToInt(QueryClusterMetricsResult.MetricSummary::getDataPointCount).sum();
        int nullValueCount = metricSummaries.stream().mapToInt(QueryClusterMetricsResult.MetricSummary::getNullValueCount).sum();
        List<String> assertMessages = new ArrayList<>();
        if (responseBody.statusCode < 200 || responseBody.statusCode >= 300) {
            assertMessages.add("[ASSERT FAIL] httpStatusCode=" + responseBody.statusCode);
        }
        if (responseCode != 0) {
            assertMessages.add("[ASSERT FAIL] response code=" + responseCode);
        }
        if (params.getExpectMinDataPoints() > 0 && dataPointCount < params.getExpectMinDataPoints()) {
            assertMessages.add("[ASSERT FAIL] dataPointCount=" + dataPointCount
                    + " < expectMinDataPoints=" + params.getExpectMinDataPoints());
        }
        if (params.isFailOnNullValue() && nullValueCount > 0) {
            assertMessages.add("[ASSERT FAIL] null metric value count=" + nullValueCount);
        }

        String result = assertMessages.stream().anyMatch(message -> message.contains("[ASSERT FAIL]"))
                ? ResultEnum.FAIL.result : ResultEnum.SUCCESS.result;
        String message = assertMessages.isEmpty() ? null : String.join("; ", assertMessages);
        return QueryClusterMetricsResult.builder()
                .commonResult(CommonResult.builder().result(result).message(message).build())
                .clusterId(clusterId)
                .url(url)
                .httpStatusCode(responseBody.statusCode)
                .responseCode(responseCode)
                .costTimeMs(costTimeMs)
                .metricCount(metricCount)
                .dataPointCount(dataPointCount)
                .metricSummaries(metricSummaries)
                .assertMessages(assertMessages)
                .response(responseJson)
                .build();
    }

    private static List<QueryClusterMetricsResult.MetricSummary> summarize(JSONObject responseJson) {
        List<QueryClusterMetricsResult.MetricSummary> summaries = new ArrayList<>();
        if (responseJson == null) {
            return summaries;
        }
        JSONObject data = responseJson.getJSONObject("data");
        if (data == null) {
            return summaries;
        }
        JSONArray results = data.getJSONArray("results");
        if (results == null) {
            return summaries;
        }
        for (int i = 0; i < results.size(); i++) {
            JSONObject metric = results.getJSONObject(i);
            if (metric == null) {
                continue;
            }
            JSONArray values = metric.getJSONArray("values");
            int dataPointCount = values == null ? 0 : values.size();
            int nullValueCount = 0;
            String firstTimestamp = null;
            String lastTimestamp = null;
            if (values != null) {
                for (int j = 0; j < values.size(); j++) {
                    JSONObject value = values.getJSONObject(j);
                    if (value == null) {
                        continue;
                    }
                    if (j == 0) {
                        firstTimestamp = value.getString("timestamp");
                    }
                    lastTimestamp = value.getString("timestamp");
                    if (!value.containsKey("value") || value.get("value") == null) {
                        nullValueCount++;
                    }
                }
            }
            summaries.add(QueryClusterMetricsResult.MetricSummary.builder()
                    .name(metric.getString("name"))
                    .unit(metric.getString("unit"))
                    .dataPointCount(dataPointCount)
                    .nullValueCount(nullValueCount)
                    .firstTimestamp(firstTimestamp)
                    .lastTimestamp(lastTimestamp)
                    .build());
        }
        return summaries;
    }

    private static QueryClusterMetricsResult failure(QueryClusterMetricsParams params, String result,
                                                    String message, long costTimeMs) {
        String clusterId = params == null ? null : firstText(params.getClusterId(),
                newInstanceInfo == null ? null : newInstanceInfo.getInstanceId());
        return QueryClusterMetricsResult.builder()
                .commonResult(CommonResult.builder().result(result).message(message).build())
                .clusterId(clusterId)
                .costTimeMs(costTimeMs)
                .assertMessages(new ArrayList<>())
                .build();
    }

    private static JSONObject parseResponse(String body) {
        if (!hasText(body)) {
            return new JSONObject(true);
        }
        try {
            return JSONObject.parseObject(body);
        } catch (Exception e) {
            JSONObject response = new JSONObject(true);
            response.put("raw", body);
            response.put("parseError", e.getMessage());
            return response;
        }
    }

    private static HttpResponseBody doPostJson(String url, Map<String, String> headers, String json,
                                               int socketTimeout) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setSocketTimeout(socketTimeout > 0 ? socketTimeout : 30000)
                .setConnectionRequestTimeout(10000)
                .build();
        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpPost httpPost = new HttpPost(url);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpPost.setHeader(entry.getKey(), entry.getValue());
            }
            httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), "utf-8");
                return new HttpResponseBody(response.getStatusLine().getStatusCode(), body);
            }
        }
    }

    private static void putIfText(JSONObject body, String key, String value) {
        if (hasText(value)) {
            body.put(key, value);
        }
    }

    private static JSONObject getData(JSONObject root) {
        if (root == null) {
            return null;
        }
        JSONObject data = root.getJSONObject("data");
        return data == null ? root.getJSONObject("Data") : data;
    }

    private static String getString(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = object.getString(key);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static Integer getInteger(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Integer value = object.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static class HttpResponseBody {
        private final int statusCode;
        private final String body;

        private HttpResponseBody(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
