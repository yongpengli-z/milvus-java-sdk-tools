package custom.components;

import custom.common.CommonFunction;
import custom.entity.SearchParams;
import custom.entity.VectorInfo;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
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

        ArrayList<Future<List<Integer>>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(searchParams.getNumConcurrency());

        float searchTotalTime = 0;
        long startTimeTotal = System.currentTimeMillis();
        Map<String,Object> searchLevel=new HashMap<>();
        searchLevel.put("level",1);
        for (int c = 0; c < searchParams.getNumConcurrency(); c++) {
            int finalC = c;
            Callable callable =
                    () -> {
                        List<BaseVector> randomBaseVectors = baseVectors;
                        log.info("线程[" + finalC + "]启动...");
                        List<Integer> results = new ArrayList<>();
                        LocalDateTime endTime = LocalDateTime.now().plusMinutes(searchParams.getRunningMinutes());
                        LocalDateTime currentTime=LocalDateTime.now();
                        int printLog=0;
                        int count=2000;
                        while (count!=0) {
//                        while (currentTime.isBefore(endTime)) {
                            if (searchParams.isRandomVector()) {
                                randomBaseVectors = CommonFunction.providerSearchVector(searchParams.getNq(), collectionVectorInfo.getDim(), collectionVectorInfo.getDataType());
                            }
                            SearchResp search = milvusClientV2.search(SearchReq.builder()
                                    .topK(searchParams.getTopK())
//                                    .outputFields(searchParams.getOutputs())
                                    .consistencyLevel(ConsistencyLevel.STRONG)
                                    .collectionName(collection)
                                    .searchParams(searchLevel)
//                                    .filter(searchParams.getFilter())
                                    .data(randomBaseVectors)
                                    .build());
                            results.add(search.getSearchResults().size());
                            if (printLog>logInterval) {
                                double passRate=(results.stream().filter(integer -> integer.intValue()>0).count())*100/results.size();
                                log.info("线程[" + finalC + "] 已经 search :" + results.size()+"次,成功率："+passRate);
                                printLog=0;
//                                currentTime=LocalDateTime.now();
                            }
                            printLog++;
                            count--;
                        }
                        return results;
                    };
            Future<List<Integer>> future = executorService.submit(callable);
            list.add(future);
        }

        long requestNum = 0;
        for (Future<List<Integer>> future : list) {
            try {
                long count = future.get().stream().filter(x -> x != 0).count();
                log.info("线程结果汇总：" + future.get());
                requestNum += count;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);

        log.info(
                "Total search " + requestNum + "次数 ,cost: " + searchTotalTime + " seconds!");
        log.info("Total 线程数 " + searchParams.getNumConcurrency() + " ,RPS avg :" + requestNum / searchTotalTime);
        executorService.shutdown();

    }
}
