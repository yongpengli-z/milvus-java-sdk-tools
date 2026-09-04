package custom.components;

import custom.entity.SearchAggregationParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SearchAggregationResult;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.aggregation.MetricOps;
import io.milvus.v2.service.vector.request.aggregation.MetricSpec;
import io.milvus.v2.service.vector.request.aggregation.OrderSpec;
import io.milvus.v2.service.vector.request.aggregation.SearchAggregation;
import io.milvus.v2.service.vector.request.aggregation.SortSpec;
import io.milvus.v2.service.vector.request.aggregation.TopHitsSpec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static custom.BaseTest.getMilvusClient;

/** Executes the SDK 3.0.4 SearchAggregation extension and returns its buckets. */
@Slf4j
public class SearchAggregationComp {
    public static SearchAggregationResult search(SearchAggregationParams params) {
        List<String> assertMessages = new ArrayList<>();
        try {
            if (params == null || params.getAggregation() == null) {
                throw new IllegalArgumentException("aggregation must be provided");
            }
            MilvusClientV2 client = getMilvusClient(params.getTargetEndpoint());
            AdvancedSearchSupport.PreparedSearch prepared = AdvancedSearchSupport.prepare(
                    client, params.getCollectionName(), params.getAnnsField(), params.getNq());
            SearchReq request = AdvancedSearchSupport.baseRequest(prepared.collection, params.getAnnsField(),
                            params.getTopK(), params.getOutputFields(), params.getFilter(),
                            params.getPartitionNames(), prepared.vectors)
                    .searchAggregation(toAggregation(params.getAggregation()))
                    .build();
            long timeout = params.getTimeout() > 0 ? params.getTimeout() : 800;
            SearchResp response = client.withTimeout(timeout, TimeUnit.MILLISECONDS).search(request);
            if (response.getAggregationBuckets() == null || response.getAggregationBuckets().isEmpty()) {
                assertMessages.add("[ASSERT WARN] search aggregation returned no buckets");
            }
            return SearchAggregationResult.builder()
                    .searchResults(response.getSearchResults())
                    .aggregationBuckets(response.getAggregationBuckets())
                    .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                    .assertMessages(assertMessages)
                    .build();
        } catch (Exception e) {
            log.error("Search aggregation failed", e);
            assertMessages.add("[ASSERT FAIL] search aggregation exception: " + e.getMessage());
            return SearchAggregationResult.builder()
                    .commonResult(CommonResult.builder().result(ResultEnum.EXCEPTION.result).message(e.getMessage()).build())
                    .assertMessages(assertMessages)
                    .build();
        }
    }

    private static SearchAggregation toAggregation(SearchAggregationParams.AggregationParams params) {
        SearchAggregation.SearchAggregationBuilder builder = SearchAggregation.builder()
                .fields(params.getFields())
                .size(params.getSize())
                .metrics(toMetrics(params.getMetrics()))
                .order(toOrder(params.getOrder()));
        if (params.getTopHits() != null) {
            builder.topHits(toTopHits(params.getTopHits()));
        }
        if (params.getSubAggregation() != null) {
            builder.subAggregation(toAggregation(params.getSubAggregation()));
        }
        return builder.build();
    }

    private static Map<String, MetricSpec> toMetrics(Map<String, SearchAggregationParams.MetricParams> metrics) {
        Map<String, MetricSpec> result = new LinkedHashMap<>();
        if (metrics == null) {
            return result;
        }
        for (Map.Entry<String, SearchAggregationParams.MetricParams> entry : metrics.entrySet()) {
            SearchAggregationParams.MetricParams metric = entry.getValue();
            if (metric == null || metric.getOp() == null) {
                throw new IllegalArgumentException("aggregation.metrics." + entry.getKey() + " must define op and fieldName");
            }
            try {
                result.put(entry.getKey(), MetricSpec.builder()
                        .op(MetricOps.valueOf(metric.getOp().trim().toUpperCase(Locale.ROOT)))
                        .fieldName(metric.getFieldName())
                        .build());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("aggregation.metrics." + entry.getKey() + ".op must be AVG, SUM, COUNT, MIN, or MAX", e);
            }
        }
        return result;
    }

    private static List<OrderSpec> toOrder(List<SearchAggregationParams.OrderParams> order) {
        List<OrderSpec> result = new ArrayList<>();
        if (order == null) {
            return result;
        }
        for (SearchAggregationParams.OrderParams item : order) {
            if (item == null) {
                throw new IllegalArgumentException("aggregation.order must not contain null entries");
            }
            result.add(OrderSpec.builder()
                    .key(item.getKey())
                    .direction(SearchOrderByComp.direction(item.getDirection(), "aggregation.order.direction"))
                    .nullFirst(item.getNullFirst())
                    .build());
        }
        return result;
    }

    private static TopHitsSpec toTopHits(SearchAggregationParams.TopHitsParams topHits) {
        TopHitsSpec.TopHitsSpecBuilder builder = TopHitsSpec.builder().size(topHits.getSize());
        List<SortSpec> sort = new ArrayList<>();
        if (topHits.getSort() != null) {
            for (SearchAggregationParams.SortParams item : topHits.getSort()) {
                if (item == null) {
                    throw new IllegalArgumentException("aggregation.topHits.sort must not contain null entries");
                }
                sort.add(SortSpec.builder()
                        .fieldName(item.getFieldName())
                        .direction(SearchOrderByComp.direction(item.getDirection(), "aggregation.topHits.sort.direction"))
                        .nullFirst(item.getNullFirst())
                        .build());
            }
        }
        return builder.sort(sort).build();
    }
}
