package custom.components;

import custom.entity.QueryIteratorParams;
import custom.entity.result.CommonResult;
import custom.entity.result.QueryIteratorResult;
import custom.entity.result.ResultEnum;
import custom.utils.MathUtil;
import custom.utils.PeriodicStatsReporter;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static custom.BaseTest.*;

@Slf4j
public class QueryIteratorComp {
    public static QueryIteratorResult queryIterator(QueryIteratorParams queryIteratorParams) {
        String collection = (queryIteratorParams.getCollectionName() == null ||
                queryIteratorParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : queryIteratorParams.getCollectionName();

        ArrayList<Future<QueryIteratorItemResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(queryIteratorParams.getNumConcurrency());

        List<String> outputFields = queryIteratorParams.getOutputFields();
        if (outputFields != null && outputFields.size() == 1 && outputFields.get(0).equalsIgnoreCase("")) {
            outputFields = new ArrayList<>();
        }

        float queryTotalTime;
        long startTimeTotal = System.currentTimeMillis();
        PeriodicStatsReporter statsReporter = new PeriodicStatsReporter("QueryIterator");
        statsReporter.start();

        for (int c = 0; c < queryIteratorParams.getNumConcurrency(); c++) {
            int finalC = c;
            List<String> finalOutputFields = outputFields;
            Callable<QueryIteratorItemResult> callable = () -> {
                log.info("线程[" + finalC + "]启动...");
                QueryIteratorItemResult itemResult = new QueryIteratorItemResult();
                List<Integer> returnNum = new ArrayList<>();
                List<Float> costTime = new ArrayList<>();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(queryIteratorParams.getRunningMinutes());
                long lastPrintTime = System.currentTimeMillis();
                while (LocalDateTime.now().isBefore(endTime)) {
                    long startItemTime = System.currentTimeMillis();
                    QueryIteratorReq.QueryIteratorReqBuilder builder = QueryIteratorReq.builder()
                            .collectionName(collection)
                            .consistencyLevel(ConsistencyLevel.BOUNDED)
                            .batchSize(queryIteratorParams.getBatchSize());
                    if (queryIteratorParams.getFilter() != null && !queryIteratorParams.getFilter().equalsIgnoreCase("")) {
                        builder.expr(queryIteratorParams.getFilter());
                    }
                    if (finalOutputFields != null && !finalOutputFields.isEmpty()) {
                        builder.outputFields(finalOutputFields);
                    }
                    if (queryIteratorParams.getLimit() > 0) {
                        builder.limit(queryIteratorParams.getLimit());
                    }
                    if (queryIteratorParams.getPartitionNames() != null && !queryIteratorParams.getPartitionNames().isEmpty()) {
                        builder.partitionNames(queryIteratorParams.getPartitionNames());
                    }
                    QueryIterator queryIterator = milvusClientV2.queryIterator(builder.build());
                    int totalCount = 0;
                    while (true) {
                        List<QueryResultsWrapper.RowRecord> res = queryIterator.next();
                        if (res.isEmpty()) {
                            queryIterator.close();
                            break;
                        }
                        totalCount += res.size();
                    }
                    returnNum.add(totalCount);
                    long endItemTime = System.currentTimeMillis();
                    float costTimeItem = (float) ((endItemTime - startItemTime) / 1000.00);
                    costTime.add(costTimeItem);
                    statsReporter.recordCostTime(costTimeItem);
                    if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                        log.info("线程[" + finalC + "] 已经 queryIterator :" + returnNum.size() + "次, 最近一次返回: " + totalCount + " 条");
                        lastPrintTime = System.currentTimeMillis();
                    }
                }
                itemResult.setResultNum(returnNum);
                itemResult.setCostTime(costTime);
                return itemResult;
            };
            Future<QueryIteratorItemResult> future = executorService.submit(callable);
            list.add(future);
        }

        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        QueryIteratorResult queryIteratorResult;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<QueryIteratorItemResult> future : list) {
            try {
                QueryIteratorItemResult itemResult = future.get();
                requestNum += itemResult.getResultNum().size();
                successNum += itemResult.getResultNum().stream().filter(x -> x > 0).count();
                costTimeTotal.addAll(itemResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("queryIterator 统计异常:" + e.getMessage());
                commonResult = CommonResult.builder()
                        .message("queryIterator 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                queryIteratorResult = QueryIteratorResult.builder().commonResult(commonResult).build();
                return queryIteratorResult;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        queryTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info("Total queryIterator " + requestNum + "次数 ,cost: " + queryTotalTime + " seconds! pass rate:" + (float) (100.0 * successNum / requestNum) + "%");
        log.info("Total 线程数 " + queryIteratorParams.getNumConcurrency() + " ,RPS avg :" + requestNum / queryTotalTime);
        log.info("Avg:" + MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:" + MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:" + MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:" + MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:" + MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:" + MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:" + MathUtil.calculateTP99(costTimeTotal, 0.50f));
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        double passRate = 100.0 * successNum / requestNum;
        // assertions
        List<String> assertMessages = new ArrayList<>();
        if (requestNum == 0) {
            assertMessages.add("[ASSERT FAIL] queryIterator requestNum == 0");
        }
        if (passRate < 50.0f) {
            assertMessages.add(String.format("[ASSERT FAIL] queryIterator passRate=%.2f%% < 50%%", passRate));
        } else if (passRate < 100.0f) {
            assertMessages.add(String.format("[ASSERT WARN] queryIterator passRate=%.2f%% < 100%%", passRate));
        }
        if (!assertMessages.isEmpty()) {
            log.warn("QueryIterator assertions: " + assertMessages);
        }
        queryIteratorResult = QueryIteratorResult.builder()
                .rps(requestNum / queryTotalTime)
                .concurrencyNum(queryIteratorParams.getNumConcurrency())
                .costTime(queryTotalTime)
                .requestNum(requestNum)
                .passRate(passRate)
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .commonResult(commonResult)
                .assertMessages(assertMessages)
                .build();
        statsReporter.stop();
        executorService.shutdown();
        return queryIteratorResult;
    }

    @Data
    public static class QueryIteratorItemResult {
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
