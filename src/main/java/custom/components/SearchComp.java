package custom.components;

import custom.common.CommonFunction;
import custom.entity.SearchParams;
import custom.entity.VectorInfo;
import custom.utils.MathUtil;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static custom.BaseTest.*;

@Slf4j
public class SearchComp {
    public static void searchCollection(SearchParams searchParams) {
        // 先search collection
        String collection = (searchParams.getCollectionName() == null ||
                searchParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(0) : searchParams.getCollectionName();
        VectorInfo collectionVectorInfo = CommonFunction.getCollectionVectorInfo(collection);
        List<BaseVector> baseVectors = CommonFunction.providerSearchVector(searchParams.getNq(), collectionVectorInfo.getDim(), collectionVectorInfo.getDataType());

        ArrayList<Future<SearchResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(searchParams.getNumConcurrency());

        float searchTotalTime = 0;
        long startTimeTotal = System.currentTimeMillis();
        Map<String,Object> searchLevel=new HashMap<>();
        searchLevel.put("level",searchParams.getSearchLevel());
        for (int c = 0; c < searchParams.getNumConcurrency(); c++) {
            int finalC = c;
            Callable<SearchResult> callable =
                    () -> {
                        List<BaseVector> randomBaseVectors = baseVectors;
                        log.info("线程[" + finalC + "]启动...");
                        SearchResult searchResult=new SearchResult();
                        List<Integer> returnNum=new ArrayList<>();
                        List<Float> costTime=new ArrayList<>();
                        LocalDateTime endTime = LocalDateTime.now().plusMinutes(searchParams.getRunningMinutes());
                        int printLog=1;
                        while (LocalDateTime.now().isBefore(endTime) ) {
                            if (searchParams.isRandomVector()) {
                                randomBaseVectors = CommonFunction.providerSearchVector(searchParams.getNq(), collectionVectorInfo.getDim(), collectionVectorInfo.getDataType());
                            }
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
                            if (printLog>=logInterval) {
                                log.info("线程[" + finalC + "] 已经 search :" + returnNum.size()+"次");
                                printLog=0;
                            }
                            printLog++;
                        }
                        searchResult.setResultNum(returnNum);
                        searchResult.setCostTime(costTime);
                        return searchResult;
                    };
            Future<SearchResult> future = executorService.submit(callable);
            list.add(future);
        }

        long requestNum = 0;
        long successNum=0;
        List<Float> costTimeTotal=new ArrayList<>();
        for (Future<SearchResult> future : list) {
            try {
                SearchResult searchResult = future.get();
                requestNum+=searchResult.getResultNum().size();
                successNum+=searchResult.getResultNum().stream().filter(x->x== searchParams.getTopK()).count();
                costTimeTotal.addAll(searchResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("search 统计异常:"+e.getMessage());
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);

        log.info(
                "Total search " + requestNum + "次数 ,cost: " + searchTotalTime + " seconds! pass rate:"+(float)(100.0*successNum/requestNum)+"%");
        log.info("Avg:"+ MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:"+MathUtil.calculateTP99(costTimeTotal,0.99f));
        log.info("TP98:"+MathUtil.calculateTP99(costTimeTotal,0.98f));
        log.info("TP90:"+MathUtil.calculateTP99(costTimeTotal,0.90f));
        log.info("TP85:"+MathUtil.calculateTP99(costTimeTotal,0.85f));
        log.info("TP80:"+MathUtil.calculateTP99(costTimeTotal,0.80f));
        log.info("TP50:"+MathUtil.calculateTP99(costTimeTotal,0.50f));
        executorService.shutdown();

    }

    @Data
    public static class SearchResult{
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
