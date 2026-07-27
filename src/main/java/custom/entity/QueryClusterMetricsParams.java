package custom.entity;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Query Zilliz Cloud cluster metrics through RESTful control plane API V2.
 */
@Data
public class QueryClusterMetricsParams {
    /**
     * Control plane base URL. Defaults to https://api.cloud.zilliz.com.
     */
    private String baseUrl;

    /**
     * Cluster ID. Empty value falls back to BaseTest.newInstanceInfo.instanceId.
     */
    private String clusterId;

    /**
     * API key or bearer token used by the control plane API.
     * Empty value falls back to JVM property, environment variable, then existing tokens.
     */
    private String apiKey;

    /**
     * JVM system property name used to read the API key. Defaults to zilliz.apiKey.
     */
    private String apiKeySystemProperty;

    /**
     * Account email used to login cloud-service and auto fetch managed API key.
     * Empty value uses the default test account.
     */
    private String accountEmail;

    /**
     * Account password used with accountEmail.
     */
    private String accountPassword;

    /**
     * Start timestamp in ISO 8601 UTC format. Use with end when period is empty.
     */
    private String start;

    /**
     * End timestamp in ISO 8601 UTC format. Use with start when period is empty.
     */
    private String end;

    /**
     * Duration in ISO 8601 format, for example PT24H. Used when start/end are empty.
     */
    private String period;

    /**
     * Reporting interval in ISO 8601 duration format. Minimum documented value is PT30S.
     */
    private String granularity;

    /**
     * Optional target database.
     */
    private String dbName;

    /**
     * Optional target collection.
     */
    private String collectionName;

    /**
     * Metric query objects. At minimum each object should contain name.
     * Example: [{"name":"CU_COMPUTATION"}]
     */
    private List<MetricQuery> metricQueries;

    /**
     * Convenience field for simple usage. Ignored when metricQueries is not empty.
     */
    private List<String> metricNames;

    /**
     * Extra request headers.
     */
    private Map<String, String> headers;

    /**
     * HTTP socket timeout in milliseconds. Defaults to 30000 when 0.
     */
    private int socketTimeout;

    /**
     * Mark the result as fail if total data points are fewer than this value.
     */
    private int expectMinDataPoints;

    /**
     * Mark the result as fail when metric values contain null.
     */
    private boolean failOnNullValue;

    /**
     * Print full response body to log.
     */
    private boolean logResponse;

    @Data
    public static class MetricQuery {
        private String name;
        private Map<String, Object> params;
    }
}
