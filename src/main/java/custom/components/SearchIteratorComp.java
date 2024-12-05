package custom.components;

import custom.common.CommonFunction;
import custom.entity.SearchIteratorParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SearchIteratorResultA;
import custom.utils.MathUtil;
import io.milvus.orm.iterator.SearchIterator;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.SearchIteratorReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

import static custom.BaseTest.*;

@Slf4j
public class SearchIteratorComp {
    public static SearchIteratorResultA searchIteratorCollection(SearchIteratorParams searchIteratorParams) {
        // 先search collection
        String collection = (searchIteratorParams.getCollectionName() == null ||
                searchIteratorParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size()-1) : searchIteratorParams.getCollectionName();

        // 随机向量，从数据库里筛选--暂定1000条
        log.info("从collection里捞取向量: " + 1000);
        List<BaseVector> searchBaseVectors = CommonFunction.providerSearchVectorDataset(collection, 1000);
        log.info("提供给search使用的随机向量数: " + searchBaseVectors.size());
        // 如果不随机，则随机一个
        List<BaseVector> baseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, searchIteratorParams.getNq());

        ArrayList<Future<SearchIteratorResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(searchIteratorParams.getNumConcurrency());
        // 处理output
        List<String> outputs = searchIteratorParams.getOutputs();
        if(outputs.size()==1&&outputs.get(0).equalsIgnoreCase("")){
            outputs=new ArrayList<>();
        }
        // metricType required options
        IndexParam.MetricType metricType;
        if (Objects.equals(searchIteratorParams.getMetricType(), "IP")) {
            metricType = IndexParam.MetricType.IP;
        } else if (Objects.equals(searchIteratorParams.getMetricType(), "COSINE")) {
            metricType = IndexParam.MetricType.COSINE;
        } else {
            metricType = IndexParam.MetricType.L2;
        }
        float searchTotalTime;
        long startTimeTotal = System.currentTimeMillis();
        for (int c = 0; c < searchIteratorParams.getNumConcurrency(); c++) {
            int finalC = c;
            List<String> finalOutputs = outputs;
            Callable<SearchIteratorResult> callable =
                    () -> {
                        List<BaseVector> randomBaseVectors = baseVectors;
                        log.info("线程[" + finalC + "]启动...");
                        SearchIteratorResult searchIteratorResult = new SearchIteratorResult();
                        List<Integer> returnNum = new ArrayList<>();
                        List<Float> costTime = new ArrayList<>();
                        LocalDateTime endTime = LocalDateTime.now().plusMinutes(searchIteratorParams.getRunningMinutes());
                        int printLog = 1;
                        while (LocalDateTime.now().isBefore(endTime)) {
                            if (searchIteratorParams.isRandomVector()) {
                                randomBaseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, searchIteratorParams.getNq());
                            }
                            long startItemTime = System.currentTimeMillis();
                            SearchIterator searchIterator = milvusClientV2.searchIterator(SearchIteratorReq.builder()
                                    .topK(searchIteratorParams.getTopK())
                                    .outputFields(finalOutputs)
                                    .vectorFieldName(searchIteratorParams.getVectorFieldName())
                                    .metricType(metricType)
                                    .consistencyLevel(ConsistencyLevel.BOUNDED)
                                    .collectionName(collection)
                                    .params(searchIteratorParams.getParams())
                                    .expr(searchIteratorParams.getFilter())
                                    .batchSize(searchIteratorParams.getBatchSize())
                                    .vectors(randomBaseVectors)
                                    .build());
                            while (true) {
                                List<QueryResultsWrapper.RowRecord> res = searchIterator.next();
                                if (res.isEmpty()) {
                                    searchIterator.close();
                                    break;
                                }
                                returnNum.add(res.size());
                            }
                            long endItemTime = System.currentTimeMillis();
                            costTime.add((float) ((endItemTime - startItemTime) / 1000.00));
                            if (printLog >= logInterval) {
                                log.info("线程[" + finalC + "] 已经 searchIterator :" + returnNum.size() + "次");
                                printLog = 0;
                            }
                            printLog++;
                        }
                        searchIteratorResult.setResultNum(returnNum);
                        searchIteratorResult.setCostTime(costTime);
                        return searchIteratorResult;
                    };
            Future<SearchIteratorResult> future = executorService.submit(callable);
            list.add(future);
        }
        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        SearchIteratorResultA searchIteratorResultA;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<SearchIteratorResult> future : list) {
            try {
                SearchIteratorResult searchIteratorResult = future.get();
                requestNum += searchIteratorResult.getResultNum().size();
                successNum += searchIteratorResult.getResultNum().stream().filter(x -> x == searchIteratorParams.getBatchSize()).count();
                costTimeTotal.addAll(searchIteratorResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("search 统计异常:" + e.getMessage());
                commonResult = CommonResult.builder()
                        .message("search 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                searchIteratorResultA = SearchIteratorResultA.builder().commonResult(commonResult).build();
                return searchIteratorResultA;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info(
                "Total search " + requestNum + "次数 ,cost: " + searchTotalTime + " seconds! pass rate:" + (float) (100.0 * successNum / requestNum) + "%");
        log.info("Total 线程数 " + searchIteratorParams.getNumConcurrency() + " ,RPS avg :" + requestNum / searchTotalTime);
        log.info("Avg:" + MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:" + MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:" + MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:" + MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:" + MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:" + MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:" + MathUtil.calculateTP99(costTimeTotal, 0.50f));
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        searchIteratorResultA = SearchIteratorResultA.builder()
                .rps(requestNum / searchTotalTime)
                .concurrencyNum(searchIteratorParams.getNumConcurrency())
                .costTime(searchTotalTime)
                .requestNum(requestNum)
                .passRate((float) (100.0 * successNum / requestNum))
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .commonResult(commonResult)
                .build();
        executorService.shutdown();
        return searchIteratorResultA;
    }

    @Data
    public static class SearchIteratorResult {
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
