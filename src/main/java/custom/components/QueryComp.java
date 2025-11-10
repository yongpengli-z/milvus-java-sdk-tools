package custom.components;

import com.google.common.util.concurrent.RateLimiter;
import custom.common.CommonFunction;
import custom.entity.QueryParams;
import custom.entity.result.CommonResult;
import custom.entity.result.QueryResult;
import custom.entity.result.ResultEnum;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.utils.MathUtil;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static custom.BaseTest.*;

@Slf4j
public class QueryComp {
    public static QueryResult queryCollection(QueryParams queryParams) {
        String collectionName = (queryParams.getCollectionName() == null ||
                queryParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : queryParams.getCollectionName();
        ArrayList<Future<QueryItemResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(queryParams.getNumConcurrency());
        float queryTotalTime;
        long startTimeTotal = System.currentTimeMillis();
        log.info("query参数：" + queryParams);
        //先处理query里数据生成的规则，先进行排序处理
        List<GeneralDataRole> generalDataRoleList = null;
        if (queryParams.getGeneralFilterRoleList() != null && queryParams.getGeneralFilterRoleList().size() > 0) {
            for (GeneralDataRole generalFilterRole : queryParams.getGeneralFilterRoleList()) {
                List<RandomRangeParams> randomRangeParamsList = generalFilterRole.getRandomRangeParamsList();
                randomRangeParamsList.sort(Comparator.comparing(RandomRangeParams::getStart));
            }
            generalDataRoleList = queryParams.getGeneralFilterRoleList().stream().filter(x -> (x.getFieldName() != null && !x.getFieldName().equalsIgnoreCase(""))).collect(Collectors.toList());

        }
        // 1. 创建RateLimiter实例（根据配置的QPS）
        RateLimiter rateLimiter = null;
        if (queryParams.getTargetQps() > 0) {
            rateLimiter = RateLimiter.create(queryParams.getTargetQps());
            log.info("启用QPS控制: {} 请求/秒", queryParams.getTargetQps());
        }

        for (int i = 0; i < queryParams.getNumConcurrency(); i++) {
            int finalI = i;
            RateLimiter finalRateLimiter = rateLimiter;
            List<GeneralDataRole> finalGeneralDataRoleList = generalDataRoleList;
            Callable<QueryItemResult> callable = () -> {
                log.info("线程[" + finalI + "]启动...");
                QueryItemResult queryItemResult = new QueryItemResult();
                List<Integer> returnNum = new ArrayList<>();
                List<Float> costTime = new ArrayList<>();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(queryParams.getRunningMinutes());
                int printLog = 1;
                while (LocalDateTime.now().isBefore(endTime)) {
                    // 3. QPS控制点（如果需要）
                    if (finalRateLimiter != null) {
                        finalRateLimiter.acquire(); // 阻塞直到获得令牌
                    }
                    long startItemTime = System.currentTimeMillis();
                    QueryResp query = null;
                    // 配置filter
                    String filterParams = queryParams.getFilter();
                    if (finalGeneralDataRoleList != null && finalGeneralDataRoleList.size() > 0) {
                        for (GeneralDataRole generalFilterRole : finalGeneralDataRoleList) {
                            int replaceFilterParams = CommonFunction.advanceRandom(generalFilterRole.getRandomRangeParamsList());
                            log.info("query random:{}", replaceFilterParams);
                            filterParams = filterParams.replaceAll("\\$" + generalFilterRole.getFieldName(), generalFilterRole.getPrefix() + replaceFilterParams);
                        }
                        log.info("query filter:{}", filterParams);
                    }
                    try {
                        QueryReq queryReq = QueryReq.builder()
                                .collectionName(collectionName)
                                .outputFields(queryParams.getOutputs())
                                .ids(queryParams.getIds().size() == 0 ? null : queryParams.getIds())
                                .filter(filterParams.equalsIgnoreCase("") ? null : filterParams)
                                .consistencyLevel(ConsistencyLevel.BOUNDED)
                                .partitionNames(queryParams.getPartitionNames() == null || queryParams.getPartitionNames().size() == 0 ? new ArrayList<>() : queryParams.getPartitionNames())
                                .offset(queryParams.getOffset())
                                .build();
                        if (queryParams.getLimit() > 0) {
                            queryReq.setLimit(queryParams.getLimit());
                        }
                        query = milvusClientV2.query(queryReq);
//                        log.info("query size: " + query.getQueryResults().get(0).getEntity());
                        log.info("query result: " + query.getQueryResults());
                    } catch (Exception e) {
                        log.error("query exception:" + e.getMessage());
                    }
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
                successNum += queryItemResult.getResultNum().stream().filter(x -> x == queryParams.getLimit()).count();
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
    public static class QueryItemResult {
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
