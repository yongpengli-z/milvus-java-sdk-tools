package custom.components;

import custom.common.CommonFunction;
import custom.entity.SearchParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SearchResultA;
import custom.utils.MathUtil;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static custom.BaseTest.*;

@Slf4j
public class SearchCompTest {
    public static SearchResultA searchCollection(SearchParams searchParams) {
        // 先search collection
        String collection = (searchParams.getCollectionName() == null ||
                searchParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size()-1) : searchParams.getCollectionName();

        // 随机向量，从数据库里筛选--暂定1000条
        log.info("从collection里捞取向量: " + 1000);
        List<BaseVector> searchBaseVectors = CommonFunction.providerSearchVectorDataset(collection, 1000,searchParams.getAnnsField());
        log.info("提供给search使用的随机向量数: " + searchBaseVectors.size());
        // 如果不随机，则随机一个
        List<BaseVector> baseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, searchParams.getNq());

        ArrayList<Future<SearchResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(searchParams.getNumConcurrency());

        float searchTotalTime;
        long startTimeTotal = System.currentTimeMillis();
        Map<String, Object> searchLevel = new HashMap<>();
        searchLevel.put("level", searchParams.getSearchLevel());
        if (searchParams.getIndexAlgo() != null && !searchParams.getIndexAlgo().equalsIgnoreCase("")) {
            searchLevel.put("index_algo", searchParams.getIndexAlgo());
        }
        for (int c = 0; c < searchParams.getNumConcurrency(); c++) {
            int finalC = c;
            Callable<SearchResult> callable =
                    () -> {
                        List<BaseVector> randomBaseVectors = baseVectors;
                        log.info("线程[" + finalC + "]启动...");
                        SearchResult searchResult = new SearchResult();
                        List<Integer> returnNum = new ArrayList<>();
                        List<Float> costTime = new ArrayList<>();
                        LocalDateTime endTime = LocalDateTime.now().plusMinutes(searchParams.getRunningMinutes());
                        int printLog = 1;
                        long itemRequestNum=0;
                        while (LocalDateTime.now().isBefore(endTime)) {
                            if (searchParams.isRandomVector()) {
                                randomBaseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, searchParams.getNq());
                            }
                            try {
                                long startItemTime = System.currentTimeMillis();
                                SearchResp search = milvusClientV2.search(SearchReq.builder()
                                        .topK(searchParams.getTopK())
                                        .outputFields(searchParams.getOutputs())
                                        .consistencyLevel(ConsistencyLevel.BOUNDED)
                                        .collectionName(collection)
                                        .searchParams(searchLevel)
                                        .filter(searchParams.getFilter())
                                        .data(randomBaseVectors)
                                        .build());
                                long endItemTime = System.currentTimeMillis();
                                costTime.add((float) ((endItemTime - startItemTime) / 1000.00));
                                returnNum.add(search.getSearchResults().size());
                            } catch (Exception e) {
                                if(printLog >= logInterval){
                                    log.warn("线程[ " + finalC + "] Search 异常" + e.getMessage());
                                    printLog = 0;
                                }
                            }
                            if (printLog >= logInterval) {
                                log.info("线程[" + finalC + "] 已经 search :" + returnNum.size() + "次");
                                printLog = 0;
                            }
                            printLog++;
                            itemRequestNum++;
                        }
                        searchResult.setResultNum(returnNum);
                        searchResult.setCostTime(costTime);
                        searchResult.setItemRequestNum(itemRequestNum);
                        return searchResult;
                    };
            Future<SearchResult> future = executorService.submit(callable);
            list.add(future);
        }
        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        SearchResultA searchResultA;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<SearchResult> future : list) {
            try {
                SearchResult searchResult = future.get();
                successNum += searchResult.getResultNum().size();
                requestNum+= searchResult.getItemRequestNum();
                costTimeTotal.addAll(searchResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("search 统计异常:" + e.getMessage());
                commonResult = CommonResult.builder()
                        .message("search 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                searchResultA = SearchResultA.builder().commonResult(commonResult).build();
                return searchResultA;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info(
                "Total search " + requestNum + "次数 ,cost: " + searchTotalTime + " seconds! pass rate:" + (float) (100.00 * successNum / requestNum) + "%");
        log.info("Total 线程数 " + searchParams.getNumConcurrency() + " ,RPS avg :" + successNum / searchTotalTime);
        log.info("Avg:" + MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:" + MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:" + MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:" + MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:" + MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:" + MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:" + MathUtil.calculateTP99(costTimeTotal, 0.50f));
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        searchResultA = SearchResultA.builder()
                .rps(successNum / searchTotalTime)
                .concurrencyNum(searchParams.getNumConcurrency())
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
        return searchResultA;
    }

    @Data
    public static class SearchResult {
        private List<Float> costTime;
        private List<Integer> resultNum;
        private long itemRequestNum;
    }
}
