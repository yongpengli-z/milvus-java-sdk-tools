package custom.entity.result;

import com.alibaba.fastjson.JSONObject;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result for QueryClusterMetricsParams.
 */
@Data
@Builder
public class QueryClusterMetricsResult {
    private CommonResult commonResult;
    private String clusterId;
    private String url;
    private int httpStatusCode;
    private int responseCode;
    private long costTimeMs;
    private int metricCount;
    private int dataPointCount;
    private List<MetricSummary> metricSummaries;
    private List<String> assertMessages;
    private JSONObject response;

    @Data
    @Builder
    public static class MetricSummary {
        private String name;
        private String unit;
        private int dataPointCount;
        private int nullValueCount;
        private String firstTimestamp;
        private String lastTimestamp;
    }
}
