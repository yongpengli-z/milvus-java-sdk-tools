package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonObject;
import custom.entity.CombinedParams;
import custom.entity.InsertParams;
import custom.entity.SearchParams;
import custom.entity.result.InsertResult;
import custom.entity.result.SearchResultA;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
public class CombinedComp {

    public static List<JSONObject> combinedComp(CombinedParams combinedParams) {
        String paramComb = combinedParams.getParamComb();
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

        Future<?> futureSearch = executorService.submit(()->{
            callCombined(operators.get(0));
        });
        Future<?> futureInsert = executorService.submit(()->{
            callCombined(operators.get(1));
        });

            // 等待任务完成
            try {
                results.add(JSON.parseObject(futureSearch.get().toString()));
                results.add(JSON.parseObject(futureInsert.get().toString()));
            } catch (Exception e) {
                log.error(e.getMessage());
            } finally {
                executorService.shutdown();
            }
        return results;
    }

    public static JSONObject callCombined(Object object) {
        JSONObject jsonObject = new JSONObject();
        if (object instanceof SearchParams) {
            log.info("*********** < [Combination] search collection > ***********");
            SearchResultA searchResultA = SearchCompTest.searchCollection((SearchParams) object);
            jsonObject.put("Search", searchResultA);
        }
        if (object instanceof InsertParams) {
            log.info("*********** < [Combination] insert data > ***********");
            InsertResult insertResult = InsertComp.insertCollection((InsertParams) object);
            jsonObject.put("Insert", insertResult);
        }
        return jsonObject;
    }
}
