package custom.components;

import custom.entity.QueryParams;
import custom.entity.result.CommonResult;
import custom.entity.result.QueryResult;
import custom.entity.result.ResultEnum;
import custom.utils.MathUtil;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static custom.BaseTest.*;

@Slf4j
public class QueryComp {
    public static QueryResult queryCollection(QueryParams queryParams) {
        String collectionName = (queryParams.getCollectionName() == null ||
                queryParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(0) : queryParams.getCollectionName();
        ArrayList<Future<QueryItemResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(queryParams.getNumConcurrency());
        float queryTotalTime;
        long startTimeTotal = System.currentTimeMillis();

        for (int i = 0; i < queryParams.getNumConcurrency(); i++) {
            int finalI = i;
            Callable<QueryItemResult> callable = ()->{
                log.info("线程[" + finalI + "]启动...");
                QueryItemResult queryItemResult=new QueryItemResult();
                List<Integer> returnNum=new ArrayList<>();
                List<Float> costTime = new ArrayList<>();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(queryParams.getRunningMinutes());
                int  printLog = 1;
                while (LocalDateTime.now().isBefore(endTime)) {
                    long startItemTime = System.currentTimeMillis();
                    QueryResp query = milvusClientV2.query(QueryReq.builder()
                            .collectionName(collectionName)
                            .outputFields(queryParams.getOutputs())
                            .ids(queryParams.getIds())
                            .filter(queryParams.getFilter())
                            .consistencyLevel(ConsistencyLevel.STRONG)
                            .partitionNames(queryParams.getPartitionNames())
                            .limit(queryParams.getLimit())
                            .offset(queryParams.getOffset())
                            .build());
                    long endItemTime = System.currentTimeMillis();
                    costTime.add((float) ((endItemTime - startItemTime) / 1000.00));
                    returnNum.add(query.getQueryResults().size());
                    if (printLog >= logInterval) {
                        log.info("线程[" + finalI + "] 已经 query :" + returnNum.size() + "次");
                        printLog = 0;
                    }
                    printLog++;
                }
                queryItemResult.setResultNum(returnNum);
                queryItemResult.setCostTime(costTime);
                return queryItemResult;
            };
            Future<QueryItemResult> future = executorService.submit(callable);
            list.add(future);
        }
        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        QueryResult queryResult;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<QueryItemResult> future : list) {
            try {
                QueryItemResult queryItemResult = future.get();
                requestNum += queryItemResult.getResultNum().size();
                successNum += queryItemResult.getResultNum().stream().filter(x->x == queryParams.getLimit()).count();
                costTimeTotal.addAll(queryItemResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("query 统计异常:" + e.getMessage());
                commonResult = CommonResult.builder()
                        .message("query 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                queryResult = QueryResult.builder().commonResult(commonResult).build();
                return queryResult;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        queryTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info(
                "Total query " + requestNum + "次数 ,cost: " + queryTotalTime + " seconds! pass rate:" + (float) (100.0 * successNum / requestNum) + "%");
        log.info("Total 线程数 " + queryParams.getNumConcurrency() + " ,RPS avg :" + requestNum / queryTotalTime);
        log.info("Avg:" + MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:" + MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:" + MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:" + MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:" + MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:" + MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:" + MathUtil.calculateTP99(costTimeTotal, 0.50f));
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        queryResult = QueryResult.builder()
                .rps(requestNum / queryTotalTime)
                .concurrencyNum(queryParams.getNumConcurrency())
                .costTime(queryTotalTime)
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
        return queryResult;
    }

    @Data
    public static class QueryItemResult{
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
