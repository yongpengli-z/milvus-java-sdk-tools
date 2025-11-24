package custom.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import custom.utils.MathUtil;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DebugTest {
    @Data
    @AllArgsConstructor
    static class DataItem {
        private long id;
        private String tenant;
    }

    public static String upsertOption() throws IOException {
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        List<String> collectionNames = listCollectionsResp.getCollectionNames();
        String collectionName = collectionNames.get(0);
        // 1. 读取文件
        Path path = Paths.get("/test/milvus/temp/upsert_data.json");
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
        int concurrencyNum = 1;
        // 使用CountDownLatch确保所有线程完成
        CountDownLatch latch = new CountDownLatch(concurrencyNum);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrencyNum);
        ArrayList<Future> list = new ArrayList<>();
        for (int c = 0; c < concurrencyNum; c++) {
            RateLimiter finalRateLimiter = rateLimiter;
            int finalC = c;
            Callable callable = () -> {
                int retryCount = 0;
                log.info("线程[" + finalC + "]启动...");
                for (int i = (600 / concurrencyNum) * finalC; i < (600 / concurrencyNum) * (finalC + 1); i++) {
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

                    if (finalRateLimiter != null) {
                        finalRateLimiter.acquire(); // 阻塞直到获得令牌
                    }
                    log.info("线程[" + finalC + "]执行批次：" + i);
                    UpsertResp collection_v = null;
                    try {
                        collection_v = milvusClientV2.upsert(UpsertReq.builder()
                                .data(jsonList)
                                .collectionName(collectionName)
                                .build());
                        if (collection_v.getUpsertCnt() > 0) {
                            retryCount = 0;
                        }
                    } catch (Exception e) {
                        log.error("线程[" + finalC + "]" + "upsert error,reason:" + e.getMessage());
// 禁写后重试判断
                        if (retryCount == 10) {
                            return null;
                        }
                        retryCount++;
                        log.info("线程[" + finalC + "]" + "第" + retryCount + "次监测到禁写，等待30秒...");
                        Thread.sleep(1000 * 30);
                        continue;

                    }
                    log.info("线程[" + finalC + "]upsert result:" + collection_v.getUpsertCnt());
                }
                return null;
            };
            Future future = executorService.submit(callable);
            list.add(future);
        }
        // 关键修改1：等待所有线程完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("result" + list);
        log.info("upsert done !");
        executorService.shutdown();
        return list.toString();
    }

    public static String upsert24hours() {
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        List<String> collectionNames = listCollectionsResp.getCollectionNames();
        String collectionName = collectionNames.get(0);
        Random random = new Random();
        Gson gson = new Gson();
        LocalDateTime endRunningTime = LocalDateTime.now().plusHours(24);
        while (LocalDateTime.now().isBefore(endRunningTime)) {
            long startTime = System.currentTimeMillis();
            // 随机10000条用户
            for (int i = 0; i < 10000; i++) {
                String user = "user_" + (random.nextInt(127982) + 17);
                QueryResp queryResp = milvusClientV2.query(QueryReq.builder()
                        .collectionName(collectionName)
                        .filter("tenant == \"" + user + "\"")
                        .outputFields(Lists.newArrayList("Int64_0"))
                        .limit(10).build());
                List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
                List<JsonObject> jsonList = new ArrayList<>();
                for (QueryResp.QueryResult queryResult : queryResults) {
                    long pk = (long) queryResult.getEntity().get("Int64_0");
                    JsonObject row = new JsonObject();
                    row.add("Int64_0", gson.toJsonTree(pk));
                    row.add("FloatVector_1", gson.toJsonTree(CommonFunction.generateFloatVector(768)));
                    row.add("tenant", gson.toJsonTree(user));
                    jsonList.add(row);
                }
                UpsertResp upsert = milvusClientV2.upsert(UpsertReq.builder()
                        .data(jsonList)
                        .collectionName(collectionName)
                        .build());
                log.info(String.format("upsert tenant %s , %s 数据.", user, upsert.getUpsertCnt()));
            }
            long endTime = System.currentTimeMillis();
            log.info(endTime + "本次10000条用户耗时：" + (endTime - startTime) / 1000.00 + "秒");
            try {
                log.info("进度等待...");
                Thread.sleep(1000 * 60 * 53);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }

        return "Done！";
    }

    public static String upsertRandomTenant() {
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        List<String> collectionNames = listCollectionsResp.getCollectionNames();
        String collectionName = collectionNames.get(0);
        Random random = new Random();
        Gson gson = new Gson();
        List<Float> costTimeTotal = new ArrayList<>();
        LocalDateTime endRunningTime = LocalDateTime.now().plusHours(4);
        while (LocalDateTime.now().isBefore(endRunningTime)) {
            String user = "user_" + (random.nextInt(127982) + 17);
            QueryResp queryResp = milvusClientV2.query(QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("tenant == \"" + user + "\"")
                    .outputFields(Lists.newArrayList("Int64_0"))
                    .limit(310).build());
            List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
            List<JsonObject> jsonList = new ArrayList<>();
            for (QueryResp.QueryResult queryResult : queryResults) {
                long pk = (long) queryResult.getEntity().get("Int64_0");
                JsonObject row = new JsonObject();
                row.add("Int64_0", gson.toJsonTree(pk));
                row.add("FloatVector_1", gson.toJsonTree(CommonFunction.generateFloatVector(768)));
                row.add("tenant", gson.toJsonTree(user));
                jsonList.add(row);
            }
            long startTimeUpsert = System.currentTimeMillis();
            UpsertResp upsert = milvusClientV2.upsert(UpsertReq.builder()
                    .data(jsonList)
                    .collectionName(collectionName)
                    .build());
            long endTimeUpsert = System.currentTimeMillis();
            log.info(String.format("Upsert tenant %s , %s data，cost：%s s", user, upsert.getUpsertCnt(), (endTimeUpsert - startTimeUpsert) / 1000.00));

            List<BaseVector> baseVectors = CommonFunction.providerSearchVector(1, 768, DataType.FloatVector);
            long startTimeSearch = System.currentTimeMillis();
            SearchResp searchResp = milvusClientV2.search(SearchReq.builder()
                    .filter("tenant == \"" + user + "\"")
                    .consistencyLevel(ConsistencyLevel.BOUNDED)
                    .topK(1)
                    .annsField("FloatVector_1")
                    .data(baseVectors)
                    .collectionName(collectionName)
                    .build());
            long endTimeSearch = System.currentTimeMillis();
            double searchCost = (endTimeSearch - startTimeSearch) / 1000.00;
            costTimeTotal.add((float) searchCost);
            log.info(String.format("Search %s,cost %s s", user, searchCost));
        }
        double avg = costTimeTotal.stream()
                .mapToDouble(Float::floatValue)
                .average()
                .orElse(0.0);
        return String.format("UpsertRandomTenant Done! Search avg: %s, tp99:  %s", avg, MathUtil.calculateTP99(costTimeTotal, 0.99f));
    }

    public static void main(String[] args) {
        String s = "{\"abnormalNum\":0,\"resultList\":[{\"ReleaseCollection_0\":{\"releaseResultList\":[[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}]]}},{\"Wait1\":{\"waitMinutes\":[10,10,10,10,10,10,10,10,10,10],\"commonResult\":{\"result\":[\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\"]}}},{\"LoadCollection_2\":{\"loadResultList\":[[{\"costTimes\":74.495,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":68.545,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":65.017,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":68.436,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":71.07,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":65.434,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":67.53,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":65.921,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":66.924,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}],[{\"costTimes\":70.458,\"collectionName\":\"Collection_PPJkvvrfkt\",\"commonResult\":{\"result\":\"success\"}}]]}},{\"Wait3\":{\"waitMinutes\":[10,10,10,10,10,10,10,10,10,10],\"commonResult\":{\"result\":[\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\",\"success\"]}}}],\"runningNum\":10}";
        JSONObject jsonObject = JSONObject.parseObject(s);
        JSONObject resultList = jsonObject.getJSONArray("resultList").getJSONObject(2);
        JSONArray jsonArray = resultList.getJSONObject("LoadCollection_2").getJSONArray("loadResultList");
        List<Double> doubleList = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            doubleList.add(jsonArray.getJSONArray(i).getJSONObject(0).getDouble("costTimes"));
        }
        System.out.println(doubleList);

//        List<Double> doubleList= Lists.newArrayList(32.257,85.102,69.977,145.426,18.655,18.663,17.138,19.155,17.652,15.63);
        double d = 0.0;
        for (int i = 0; i < doubleList.size(); i++) {
            d += doubleList.get(i);
        }
        System.out.println(d / 10);

    }
}
