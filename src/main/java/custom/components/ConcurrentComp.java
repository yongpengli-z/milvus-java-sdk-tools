package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import custom.entity.*;
import custom.entity.result.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
public class ConcurrentComp {

    public static List<JSONObject> concurrentComp(ConcurrentParams concurrentParams) {
        String paramComb = concurrentParams.getParamComb();
        JSONObject paramCombJO = JSON.parseObject(paramComb);
        Set<String> keyList = paramCombJO.keySet();
        List<Object> operators = new ArrayList<>();
        for (String keyString : keyList) {
            String itemParam = paramCombJO.getString(keyString);
            try {
                Object o = JSONObject.parseObject(itemParam, Class.forName("custom.entity." + keyString));
                operators.add(o);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        // 先起线程
        ExecutorService executorService = Executors.newFixedThreadPool(operators.size());
        List<JSONObject> results = new ArrayList<>();
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < operators.size(); i++) {
            final int threadNumber = i;
            Callable<String> task = () -> {
                JSONObject jsonObject = callConcurrent(operators.get(threadNumber));
                return jsonObject.toJSONString();
            };
            futures.add(executorService.submit(task));
        }
        // 获取每个线程的返回值
        for (Future<String> future : futures) {
            try {
                String result = future.get(); // 阻塞直到任务完成并获取结果
                results.add(JSON.parseObject(result));
            } catch (Exception e) {
                log.error("Combine Result Exception:" + e.getMessage());
            }
        }
        executorService.shutdown(); // 关闭线程池
        return results;
    }

    public static JSONObject callConcurrent(Object object) {
        JSONObject jsonObject = new JSONObject();
        if (object instanceof SearchParams) {
            log.info("--------------- < [Concurrent] search collection > ---------------");
            SearchResultA searchResultA = SearchCompTest.searchCollection((SearchParams) object);
            jsonObject.put("Search", searchResultA);
        }
        if (object instanceof InsertParams) {
            log.info("--------------- < [Concurrent] insert data > ---------------");
            InsertResult insertResult = InsertComp.insertCollection((InsertParams) object);
            jsonObject.put("Insert", insertResult);
        }
        if (object instanceof UpsertParams) {
            log.info("--------------- < [Concurrent] upsert data > ---------------");
            UpsertResult upsertResult = UpsertComp.upsertCollection((UpsertParams) object);
            jsonObject.put("Upsert", upsertResult);
        }
        if (object instanceof QueryParams) {
            log.info("--------------- < [Concurrent] query collection > ---------------");
            QueryResult queryResult = QueryComp.queryCollection((QueryParams) object);
            jsonObject.put("Query", queryResult);
        }
        if (object instanceof CreateCollectionParams) {
            log.info("--------------- < [Concurrent] create collection > ---------------");
            CreateCollectionResult createCollectionResult = CreateCollectionComp.createCollection((CreateCollectionParams) object);
            jsonObject.put("CreateCollection", createCollectionResult);
        }
        if (object instanceof CreateIndexParams) {
            log.info("--------------- < [Concurrent] create index > ---------------");
            CreateIndexResult createIndexResult = CreateIndexComp.CreateIndex((CreateIndexParams) object);
            jsonObject.put("CreateIndex", createIndexResult);
        }
        if (object instanceof LoadParams) {
            log.info("--------------- < [Concurrent] load collection > ---------------");
            LoadResult loadResult = LoadCollectionComp.loadCollection((LoadParams) object);
            jsonObject.put("LoadCollection", loadResult);
        }
        if (object instanceof ReleaseParams) {
            log.info("--------------- < [Concurrent] release collection > ---------------");
            ReleaseResult releaseResult = ReleaseCollectionComp.releaseCollection((ReleaseParams) object);
            jsonObject.put("ReleaseCollection", releaseResult);
        }
        if (object instanceof DropCollectionParams) {
            log.info("--------------- < [Concurrent] drop collection > ---------------");
            DropCollectionResult dropCollectionResult = DropCollectionComp.dropCollection((DropCollectionParams) object);
            jsonObject.put("DropCollection", dropCollectionResult);
        }
        if (object instanceof WaitParams) {
            log.info("--------------- < [Concurrent] wait > ---------------");
            WaitResult waitResult = WaitComp.wait((WaitParams) object);
            jsonObject.put("Wait", waitResult);
        }
        if (object instanceof DropIndexParams) {
            log.info("--------------- < [Concurrent] drop index > ---------------");
            DropIndexResult dropIndexResult = DropIndexComp.dropIndex((DropIndexParams) object);
            jsonObject.put("DropIndex", dropIndexResult);
        }
        return jsonObject;
    }
}
