package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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

    public static List<JSONObject> combinedComp(CombinedParams combinedParams){
        String paramComb = combinedParams.getParamComb();
        JSONObject paramCombJO=JSON.parseObject(paramComb);
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

        for (int i = 0; i < operators.size(); i++){

            int finalI = i;
            Callable callable=
                    ()->{

                        if (operators.get(finalI) instanceof SearchParams) {
                            log.info("*********** < [Combination] search collection > ***********");
                            SearchResultA searchResultA = SearchCompTest.searchCollection((SearchParams) operators.get(finalI));
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("Search_" + finalI, searchResultA);
                            results.add(jsonObject);
                        }
                        if (operators.get(finalI) instanceof InsertParams) {
                            log.info("*********** < [Combination] insert data > ***********");
                            InsertResult insertResult = InsertComp.insertCollection((InsertParams) operators.get(finalI));
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("Insert_" + finalI, insertResult);
                            results.add(jsonObject);
                        }
                        return null;
                    };
           executorService.submit(callable);


        }
        return results;

    }

}
