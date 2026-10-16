package custom.entity.result;

import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.aggregation.AggregationBucket;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchAggregationResult {
    private CommonResult commonResult;
    private List<List<SearchResp.SearchResult>> searchResults;
    private List<List<AggregationBucket>> aggregationBuckets;
    private List<String> assertMessages;
    private Integer concurrencyNum;
    private Long requestNum;
    private Long rpcSuccessNum;
    private Long rpcFailureNum;
    private Double rps;
    private Double requestRps;
    private Double avg;
    private Double tp50;
    private Double tp90;
    private Double tp99;
    private Double costTime;
}
