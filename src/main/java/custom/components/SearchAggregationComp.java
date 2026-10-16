package custom.components;

import custom.entity.SearchAggregationParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SearchAggregationResult;
import custom.utils.MathUtil;
import custom.utils.PeriodicStatsReporter;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.aggregation.MetricOps;
import io.milvus.v2.service.vector.request.aggregation.MetricSpec;
import io.milvus.v2.service.vector.request.aggregation.OrderSpec;
import io.milvus.v2.service.vector.request.aggregation.SearchAggregation;
import io.milvus.v2.service.vector.request.aggregation.SortSpec;
import io.milvus.v2.service.vector.request.aggregation.TopHitsSpec;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.request.data.BaseVector;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
            if (params.getNumConcurrency() < 0 || params.getRunningMinutes() < 0 || params.getRunningCount() < 0) {
                throw new IllegalArgumentException("numConcurrency, runningMinutes and runningCount must be nonnegative");
            }
            MilvusClientV2 client = getMilvusClient(params.getTargetEndpoint());
            AdvancedSearchSupport.PreparedSearch prepared = AdvancedSearchSupport.prepare(
                    client, params.getCollectionName(), params.getAnnsField(), params.getNq(), params.getQueryVectors());
            SearchAggregation aggregation = toAggregation(params.getAggregation());
            if (params.getRunningMinutes() > 0 || params.getRunningCount() > 0) {
                return runPerformance(client, params, prepared, aggregation);
            }
            SearchReq request = AdvancedSearchSupport.baseRequest(prepared.collection, params.getAnnsField(),
                            params.getTopK(), params.getOutputFields(), params.getFilter(),
                            params.getPartitionNames(), AdvancedSearchSupport.queryBatch(prepared.vectors, params.getNq(), 0))
                    .searchAggregation(aggregation)
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
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Search aggregation failed", e);
            assertMessages.add("[ASSERT FAIL] search aggregation exception: " + e.getMessage());
            return SearchAggregationResult.builder()
                    .commonResult(CommonResult.builder().result(ResultEnum.EXCEPTION.result).message(e.getMessage()).build())
                    .assertMessages(assertMessages)
                    .build();
        }
    }

    private static SearchAggregationResult runPerformance(MilvusClientV2 client, SearchAggregationParams params,
                                                           AdvancedSearchSupport.PreparedSearch prepared,
                                                           SearchAggregation aggregation) throws InterruptedException, ExecutionException {
        int concurrency = params.getNumConcurrency() == 0 ? 1 : params.getNumConcurrency();
        long timeout = params.getTimeout() > 0 ? params.getTimeout() : 800;
        long started = System.nanoTime();
        long deadline = started + TimeUnit.MINUTES.toNanos(params.getRunningMinutes());
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        PeriodicStatsReporter reporter = new PeriodicStatsReporter("SearchAggregation");
        List<Future<WorkerStats>> futures = new ArrayList<>();
        reporter.start();
        try {
            for (int worker = 0; worker < concurrency; worker++) {
                final int workerIndex = worker;
                Callable<WorkerStats> task = () -> {
                    WorkerStats stats = new WorkerStats();
                    while (params.getRunningCount() > 0 ? stats.requestNum < params.getRunningCount()
                            : System.nanoTime() < deadline) {
                        List<BaseVector> vectors = AdvancedSearchSupport.queryBatch(prepared.vectors, params.getNq(),
                                workerIndex + stats.requestNum * params.getNq());
                        SearchReq request = AdvancedSearchSupport.baseRequest(prepared.collection, params.getAnnsField(),
                                        params.getTopK(), params.getOutputFields(), params.getFilter(),
                                        params.getPartitionNames(), vectors)
                                .searchAggregation(aggregation).build();
                        long requestStarted = System.nanoTime();
                        stats.requestNum++;
                        try {
                            client.withTimeout(timeout, TimeUnit.MILLISECONDS).search(request);
                            float latency = (System.nanoTime() - requestStarted) / 1_000_000_000f;
                            stats.rpcSuccessNum++;
                            stats.latencies.add(latency);
                            reporter.recordCostTime(latency);
                        } catch (Exception e) {
                            stats.rpcFailureNum++;
                            reporter.recordFailure();
                            if (stats.rpcFailureNum == 1) {
                                log.warn("Search aggregation worker {} first request failure: {}", workerIndex, e.getMessage());
                            }
                        }
                    }
                    return stats;
                };
                futures.add(executor.submit(task));
            }
            long requestNum = 0;
            long successNum = 0;
            long failureNum = 0;
            List<Float> latencies = new ArrayList<>();
            for (Future<WorkerStats> future : futures) {
                WorkerStats stats = future.get();
                requestNum += stats.requestNum;
                successNum += stats.rpcSuccessNum;
                failureNum += stats.rpcFailureNum;
                latencies.addAll(stats.latencies);
            }
            double elapsed = (System.nanoTime() - started) / 1_000_000_000d;
            List<String> messages = new ArrayList<>();
            if (requestNum == 0) {
                messages.add("[ASSERT FAIL] no search aggregation request was executed");
            } else if (failureNum > 0) {
                messages.add("[ASSERT WARN] " + failureNum + " search aggregation RPC requests failed");
            }
            CommonResult commonResult = CommonResult.builder()
                    .result(successNum == 0 ? ResultEnum.EXCEPTION.result
                            : failureNum > 0 ? ResultEnum.WARNING.result : ResultEnum.SUCCESS.result).build();
            return SearchAggregationResult.builder()
                    .commonResult(commonResult).assertMessages(messages)
                    .concurrencyNum(concurrency).requestNum(requestNum)
                    .rpcSuccessNum(successNum).rpcFailureNum(failureNum)
                    .rps(successNum / elapsed).requestRps(requestNum / elapsed).costTime(elapsed)
                    .avg(MathUtil.calculateAverage(latencies))
                    .tp50(MathUtil.calculateTP99(latencies, 0.50f))
                    .tp90(MathUtil.calculateTP99(latencies, 0.90f))
                    .tp99(MathUtil.calculateTP99(latencies, 0.99f))
                    .build();
        } finally {
            reporter.stop();
            executor.shutdownNow();
        }
    }

    private static final class WorkerStats {
        private long requestNum;
        private long rpcSuccessNum;
        private long rpcFailureNum;
        private final List<Float> latencies = new ArrayList<>();
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
