package custom.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DebugTest {
    @Data
    @AllArgsConstructor
    static class DataItem {
        private long id;
        private String tenant;
    }

    public static void upsertOption() throws IOException {
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        List<String> collectionNames = listCollectionsResp.getCollectionNames();
        String collectionName=collectionNames.get(0);
        // 1. 读取文件
        Path path = Paths.get("test/milvus/temp/upsert_data.json");
        String content = Files.readString(path);

        // 2. 解析JSON
        JSONObject root = JSON.parseObject(content);
        JSONArray rawArray = root.getJSONArray("raw");

        // 3. 转换为List
        List<DataItem> result = new ArrayList<>(rawArray.size());
        for (int i = 0; i < rawArray.size(); i++) {
            JSONObject item = rawArray.getJSONObject(i);
            result.add(new DataItem(
                    item.getLong("Int64_0"),
                    item.getString("tenant")
            ));
        }

        // 4. 使用数据
        log.info("第一条记录: " + result.get(0));
        log.info("总记录数: " + result.size());

        Gson gson = new Gson();
        // 1. 创建RateLimiter实例（根据配置的QPS）
        RateLimiter rateLimiter = null;
        rateLimiter = RateLimiter.create(1);
        int concurrencyNum=10;
        ExecutorService executorService = Executors.newFixedThreadPool(concurrencyNum);
        for (int c = 0; c < concurrencyNum; c++) {
            RateLimiter finalRateLimiter = rateLimiter;
            int finalC = c;
            Callable callable = () -> {
                log.info("线程[" + finalC + "]启动...");
                for (int i = (600/concurrencyNum)*finalC; i < (600/concurrencyNum)*(finalC+1); i++) {
                    int startId = i * 12500;
                    int endId = startId + 12500;
                    List<JsonObject> jsonList = new ArrayList<>();
                    for (int j = startId; j < endId; j++) {
                        JsonObject row = new JsonObject();
                        row.add("Int64_0", gson.toJsonTree(result.get(j).getId()));
                        row.add("FloatVector_1", gson.toJsonTree(CommonFunction.generateFloatVector(768)));
                        row.add("tenant", gson.toJsonTree(gson.toJsonTree(result.get(j).getTenant())));
                        jsonList.add(row);
                    }
                    log.info("线程[" + finalC + "]执行批次："+i);
                    if (finalRateLimiter != null) {
                        finalRateLimiter.acquire(); // 阻塞直到获得令牌
                    }
                    UpsertResp collection_v = milvusClientV2.upsert(UpsertReq.builder()
                            .data(jsonList)
                            .collectionName(collectionName)
                            .build());
                    log.info("线程[" + finalC + "]upsert result:"+ collection_v.getUpsertCnt());
                }
                return null;
            };
            Future future = executorService.submit(callable);

        }
        log.info("upsert done !");
    }
}
