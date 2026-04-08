package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.RateLimiter;
import custom.common.CommonFunction;
import custom.entity.RestfulHybridSearchParams;
import custom.entity.result.CommonResult;
import custom.entity.result.RestfulHybridSearchResult;
import custom.entity.result.ResultEnum;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.utils.MathUtil;
import custom.utils.PeriodicStatsReporter;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.data.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static custom.BaseTest.*;

/**
 * RESTful HybridSearch（混合搜索）组件。
 * <p>
 * 通过 Milvus RESTful API（/v2/vectordb/entities/advanced_search）对同一个 collection 中
 * 的多个向量字段进行搜索，并使用融合策略（RRF / WeightedRanker）合并结果。
 * <p>
 * 与 {@link HybridSearchComp} 行为对齐，仅替换 SDK 调用为 HTTP 调用。
 */
@Slf4j
public class RestfulHybridSearchComp {

    public static RestfulHybridSearchResult restfulHybridSearchCollection(RestfulHybridSearchParams hybridSearchParams) {
        // 判断collection获取规则
        Random random = new Random();
        String collection;
        if (hybridSearchParams.getCollectionRule() == null || hybridSearchParams.getCollectionRule().equalsIgnoreCase("")) {
            collection = (hybridSearchParams.getCollectionName() == null ||
                    hybridSearchParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : hybridSearchParams.getCollectionName();
        } else if (hybridSearchParams.getCollectionRule().equalsIgnoreCase("random")) {
            collection = globalCollectionNames.get(random.nextInt(globalCollectionNames.size()));
        } else if (hybridSearchParams.getCollectionRule().equalsIgnoreCase("sequence")) {
            collection = globalCollectionNames.get(searchCollectionIndex);
            searchCollectionIndex += 1;
            searchCollectionIndex = searchCollectionIndex % globalCollectionNames.size();
        } else {
            collection = (hybridSearchParams.getCollectionName() == null ||
                    hybridSearchParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : hybridSearchParams.getCollectionName();
        }

        // 验证 searchRequests 不为空
        if (hybridSearchParams.getSearchRequests() == null || hybridSearchParams.getSearchRequests().isEmpty()) {
            log.error("RestfulHybridSearch searchRequests 不能为空");
            CommonResult commonResult = CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message("RestfulHybridSearch searchRequests 不能为空")
                    .build();
            return RestfulHybridSearchResult.builder()
                    .commonResult(commonResult)
                    .build();
        }

        // 处理 filter 占位符替换规则
        List<GeneralDataRole> generalDataRoleList = null;
        if (hybridSearchParams.getGeneralFilterRoleList() != null && hybridSearchParams.getGeneralFilterRoleList().size() > 0) {
            for (GeneralDataRole generalFilterRole : hybridSearchParams.getGeneralFilterRoleList()) {
                List<RandomRangeParams> randomRangeParamsList = generalFilterRole.getRandomRangeParamsList();
                if (randomRangeParamsList != null) {
                    randomRangeParamsList.sort(Comparator.comparing(RandomRangeParams::getStart));
                }
            }
            generalDataRoleList = hybridSearchParams.getGeneralFilterRoleList().stream()
                    .filter(x -> (x.getFieldName() != null && !x.getFieldName().equalsIgnoreCase("")))
                    .collect(Collectors.toList());
        }

        // 判定是不是sparse向量，并且是由Function BM25生成
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collection).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.Function> functionList = collectionSchema.getFunctionList();

        // 构建 annsField -> inputFieldName 的映射（用于BM25 Function字段）
        Map<String, String> functionFieldMap = new HashMap<>();
        for (CreateCollectionReq.Function function : functionList) {
            for (int i = 0; i < function.getOutputFieldNames().size(); i++) {
                String outputField = function.getOutputFieldNames().get(i);
                String inputField = function.getInputFieldNames().get(i);
                functionFieldMap.put(outputField, inputField);
                log.info("检测到BM25 Function: outputField={}, inputField={}", outputField, inputField);
            }
        }

        // 为每个搜索请求准备向量数据
        Map<String, List<BaseVector>> fieldVectorsMap = new HashMap<>();
        for (RestfulHybridSearchParams.RestfulHybridSearchRequest request : hybridSearchParams.getSearchRequests()) {
            String annsField = request.getAnnsField();
            if (annsField == null || annsField.equalsIgnoreCase("")) {
                log.error("RestfulHybridSearchRequest annsField 不能为空");
                continue;
            }

            // 检查该字段是否由BM25 Function生成
            if (functionFieldMap.containsKey(annsField)) {
                String inputFieldName = functionFieldMap.get(annsField);
                log.info("字段 {} 由BM25 Function生成，从collection里捞取input field {} 的文本数据: {}", annsField, inputFieldName, 1000);
                List<BaseVector> searchBaseVectors = CommonFunction.providerSearchFunctionData(collection, 1000, inputFieldName);
                log.info("提供给restfulHybridSearch使用的随机文本数量: {}", searchBaseVectors.size());
                fieldVectorsMap.put(annsField, searchBaseVectors);
            } else {
                // 从 collection 中采样向量
                log.info("从collection里捞取向量字段 {} 的向量: {}", annsField, 1000);
                List<BaseVector> searchBaseVectors = CommonFunction.providerSearchVectorDataset(collection, 1000, annsField);
                log.info("提供给restfulHybridSearch使用的随机向量数: {}", searchBaseVectors.size());
                fieldVectorsMap.put(annsField, searchBaseVectors);
            }
        }

        // 准备初始查询向量--先提供不随机时候的向量
        Map<String, List<BaseVector>> initialVectorsMap = new HashMap<>();
        for (Map.Entry<String, List<BaseVector>> entry : fieldVectorsMap.entrySet()) {
            List<BaseVector> baseVectors = CommonFunction.providerSearchVectorByNq(entry.getValue(), hybridSearchParams.getNq());
            initialVectorsMap.put(entry.getKey(), baseVectors);
        }

        // 构建RESTful请求URL和headers
        String baseUri = newInstanceInfo.getUri();
        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }
        String hybridSearchUrl = baseUri + "/v2/vectordb/entities/advanced_search";
        String token = newInstanceInfo.getToken();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }

        ArrayList<Future<RestfulHybridSearchResultInner>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(hybridSearchParams.getNumConcurrency());

        // 处理output
        List<String> outputs = hybridSearchParams.getOutputs();
        if (outputs == null || (outputs.size() == 1 && outputs.get(0).equalsIgnoreCase(""))) {
            outputs = new ArrayList<>();
        }

        // 准备融合策略参数
        Map<String, Object> rankerParams = hybridSearchParams.getRankerParams();
        if (rankerParams == null) {
            rankerParams = new HashMap<>();
        }
        String rankerType = hybridSearchParams.getRanker();
        if (rankerType == null || rankerType.equalsIgnoreCase("")) {
            rankerType = "RRF";
        }
        // 如果使用 RRF 且没有设置 k，使用默认值
        if ("RRF".equalsIgnoreCase(rankerType) && !rankerParams.containsKey("k")) {
            rankerParams.put("k", 60);
        }
        // 如果使用 WeightedRanker 且没有设置 weights，使用平均权重
        if ("WeightedRanker".equalsIgnoreCase(rankerType) && !rankerParams.containsKey("weights")) {
            int numRequests = hybridSearchParams.getSearchRequests().size();
            List<Double> weights = new ArrayList<>();
            for (int i = 0; i < numRequests; i++) {
                weights.add(1.0 / numRequests);
            }
            rankerParams.put("weights", weights);
        }

        float searchTotalTime;
        long startTimeTotal = System.currentTimeMillis();

        // 创建RateLimiter实例（根据配置的QPS）
        RateLimiter rateLimiter = null;
        if (hybridSearchParams.getTargetQps() > 0) {
            rateLimiter = RateLimiter.create(hybridSearchParams.getTargetQps());
            log.info("启用QPS控制: {} 请求/秒", hybridSearchParams.getTargetQps());
        }

        final String finalCollection = collection;
        final List<String> finalOutputs = outputs;
        final RateLimiter finalRateLimiter = rateLimiter;
        final List<GeneralDataRole> finalGeneralDataRoleList = generalDataRoleList;
        final Map<String, Object> finalRankerParams = rankerParams;
        final String finalRankerType = rankerType;
        final String finalHybridSearchUrl = hybridSearchUrl;

        PeriodicStatsReporter statsReporter = new PeriodicStatsReporter("RestfulHybridSearch");
        statsReporter.start();
        for (int c = 0; c < hybridSearchParams.getNumConcurrency(); c++) {
            int finalC = c;
            Callable<RestfulHybridSearchResultInner> callable = () -> {
                log.info("线程[{}]启动...", finalC);
                RestfulHybridSearchResultInner result = new RestfulHybridSearchResultInner();
                List<Integer> returnNum = new ArrayList<>();
                List<Float> costTime = new ArrayList<>();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(hybridSearchParams.getRunningMinutes());
                int requestCount = 0;
                long lastLogTime = System.currentTimeMillis();
                long lastPrintTime = System.currentTimeMillis();

                // 准备当前线程的向量数据
                Map<String, List<BaseVector>> threadVectorsMap = new HashMap<>();
                for (Map.Entry<String, List<BaseVector>> entry : initialVectorsMap.entrySet()) {
                    threadVectorsMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }

                while (LocalDateTime.now().isBefore(endTime)) {
                    if (finalRateLimiter != null) {
                        finalRateLimiter.acquire();
                    }

                    // 如果需要随机向量，重新采样
                    if (hybridSearchParams.isRandomVector()) {
                        for (RestfulHybridSearchParams.RestfulHybridSearchRequest request : hybridSearchParams.getSearchRequests()) {
                            String annsField = request.getAnnsField();
                            List<BaseVector> baseVectors = fieldVectorsMap.get(annsField);
                            if (baseVectors != null) {
                                List<BaseVector> randomVectors = CommonFunction.providerSearchVectorByNq(baseVectors, hybridSearchParams.getNq());
                                threadVectorsMap.put(annsField, randomVectors);
                            }
                        }
                    }

                    // 构建多个 sub-search 请求体
                    JSONArray subSearchArray = new JSONArray();
                    for (RestfulHybridSearchParams.RestfulHybridSearchRequest request : hybridSearchParams.getSearchRequests()) {
                        String annsField = request.getAnnsField();
                        List<BaseVector> vectors = threadVectorsMap.get(annsField);
                        if (vectors == null || vectors.isEmpty()) {
                            log.warn("线程[{}] 字段 {} 的向量为空，跳过", finalC, annsField);
                            continue;
                        }

                        // 准备 searchParams
                        Map<String, Object> searchParams = request.getSearchParams();
                        if (searchParams == null) {
                            searchParams = new HashMap<>();
                            searchParams.put("level", 1);
                        } else if (!searchParams.containsKey("level")) {
                            searchParams.put("level", 1);
                        }

                        // 处理 filter
                        String filter = request.getFilter();
                        if (filter != null && !filter.equalsIgnoreCase("")) {
                            if (finalGeneralDataRoleList != null && finalGeneralDataRoleList.size() > 0) {
                                String processedFilter = filter;
                                for (GeneralDataRole generalFilterRole : finalGeneralDataRoleList) {
                                    int replaceFilterParams;
                                    if (generalFilterRole.getSequenceOrRandom().equalsIgnoreCase("sequence")) {
                                        replaceFilterParams = CommonFunction.advanceSequenceForSearch(
                                                generalFilterRole.getRandomRangeParamsList(),
                                                hybridSearchParams.getNumConcurrency(),
                                                finalC,
                                                returnNum.size());
                                    } else {
                                        replaceFilterParams = CommonFunction.advanceRandom(generalFilterRole.getRandomRangeParamsList());
                                    }
                                    processedFilter = processedFilter.replaceAll("\\$" + generalFilterRole.getFieldName(), generalFilterRole.getPrefix() + replaceFilterParams);
                                }
                                filter = processedFilter;
                                if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                                    log.info("线程[{}] 字段[{}] restfulHybridSearch filter:{}", finalC, annsField, filter);
                                }
                            }
                        }

                        JSONObject subSearch = new JSONObject();
                        subSearch.put("annsField", annsField);
                        subSearch.put("limit", request.getTopK());
                        subSearch.put("data", buildVectorDataArray(vectors));
                        if (filter != null && !filter.isEmpty()) {
                            subSearch.put("filter", filter);
                        }
                        if (request.getMetricType() != null && !request.getMetricType().isEmpty()) {
                            subSearch.put("metricType", request.getMetricType());
                        }
                        // params 内层
                        JSONObject paramsJson = new JSONObject();
                        paramsJson.putAll(searchParams);
                        subSearch.put("params", paramsJson);

                        subSearchArray.add(subSearch);
                    }

                    if (subSearchArray.isEmpty()) {
                        log.warn("线程[{}] subSearchArray 为空，跳过本次请求", finalC);
                        continue;
                    }

                    // 构建顶层请求体
                    JSONObject requestBody = new JSONObject();
                    requestBody.put("collectionName", finalCollection);
                    requestBody.put("search", subSearchArray);
                    requestBody.put("limit", hybridSearchParams.getTopK());
                    if (!finalOutputs.isEmpty()) {
                        requestBody.put("outputFields", finalOutputs);
                    }

                    // rerank（融合策略）
                    JSONObject rerank = new JSONObject();
                    rerank.put("strategy", finalRankerType);
                    JSONObject rerankParamsJson = new JSONObject();
                    rerankParamsJson.putAll(finalRankerParams);
                    rerank.put("params", rerankParamsJson);
                    requestBody.put("rerank", rerank);

                    long startItemTime = System.currentTimeMillis();
                    try {
                        String response = doPostJsonQuietly(finalHybridSearchUrl, headers, requestBody.toJSONString(),
                                hybridSearchParams.getSocketTimeout());
                        long endItemTime = System.currentTimeMillis();
                        float costTimeItem = (float) ((endItemTime - startItemTime) / 1000.00);
                        costTime.add(costTimeItem);
                        statsReporter.recordCostTime(costTimeItem);

                        // 解析响应
                        JSONObject responseJson = JSON.parseObject(response);
                        int code = responseJson.getIntValue("code");
                        if (code == 0 || code == 200) {
                            JSONArray data = responseJson.getJSONArray("data");
                            if (data != null) {
                                returnNum.add(data.size());
                            } else {
                                returnNum.add(0);
                            }
                        } else {
                            statsReporter.recordFailure();
                            String message = responseJson.getString("message");
                            log.error("线程[{}] restful hybridSearch error, code:{}, message:{}", finalC, code, message);
                            if (hybridSearchParams.isIgnoreError()) {
                                log.error("线程[{}] Ignore error, continue restful hybridSearch......", finalC);
                            }
                            returnNum.add(0);
                        }
                    } catch (Exception e) {
                        statsReporter.recordFailure();
                        log.error("线程[{}] restful hybridSearch error :{}", finalC, e.getMessage());
                        if (hybridSearchParams.isIgnoreError()) {
                            log.error("线程[{}] Ignore error, continue restful hybridSearch......", finalC);
                            returnNum.add(0);
                            continue;
                        }
                        throw e;
                    }

                    if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                        log.info("线程[{}] 已经 restful hybridSearch :{}次", finalC, returnNum.size());
                        lastPrintTime = System.currentTimeMillis();
                    }

                    requestCount++;
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastLogTime > 5000) {
                        double actualQps = requestCount / ((currentTime - lastLogTime) / 1000.0);
                        log.debug("线程[{}] 当前QPS: {}", finalC, actualQps);
                        requestCount = 0;
                        lastLogTime = currentTime;
                    }
                }

                result.setResultNum(returnNum);
                result.setCostTime(costTime);
                return result;
            };
            Future<RestfulHybridSearchResultInner> future = executorService.submit(callable);
            list.add(future);
        }

        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        RestfulHybridSearchResult hybridSearchResult;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<RestfulHybridSearchResultInner> future : list) {
            try {
                RestfulHybridSearchResultInner result = future.get();
                requestNum += result.getResultNum().size();
                successNum += result.getResultNum().stream().filter(x -> x > 0).count();
                if (result.getCostTime() != null) {
                    costTimeTotal.addAll(result.getCostTime());
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("restful hybridSearch 统计异常:{}", e.getMessage());
                commonResult = CommonResult.builder()
                        .message("restful hybridSearch 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                hybridSearchResult = RestfulHybridSearchResult.builder().commonResult(commonResult).build();
                executorService.shutdown();
                return hybridSearchResult;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info("Total restful hybridSearch {} 次数 ,cost: {} seconds! pass rate:{}%",
                requestNum, searchTotalTime, (float) (100.0 * successNum / requestNum));
        log.info("Total 线程数 {} ,RPS avg :{}", hybridSearchParams.getNumConcurrency(), requestNum / searchTotalTime);
        log.info("Avg:{}", MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:{}", MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:{}", MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:{}", MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:{}", MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:{}", MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:{}", MathUtil.calculateTP99(costTimeTotal, 0.50f));

        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        double passRate = 100.0 * successNum / requestNum;
        // assertions
        List<String> assertMessages = new ArrayList<>();
        if (requestNum == 0) {
            assertMessages.add("[ASSERT FAIL] restful hybridSearch requestNum == 0, no search was executed");
        }
        if (passRate < 50.0f) {
            assertMessages.add(String.format("[ASSERT FAIL] restful hybridSearch passRate=%.2f%% < 50%%, %d/%d requests returned results",
                    passRate, successNum, requestNum));
        } else if (passRate < 100.0f) {
            assertMessages.add(String.format("[ASSERT WARN] restful hybridSearch passRate=%.2f%% < 100%%, %d/%d requests returned results",
                    passRate, successNum, requestNum));
        }
        if (!assertMessages.isEmpty()) {
            log.warn("RestfulHybridSearch assertions: " + assertMessages);
        }
        hybridSearchResult = RestfulHybridSearchResult.builder()
                .rps(requestNum / searchTotalTime)
                .concurrencyNum(hybridSearchParams.getNumConcurrency())
                .costTime(searchTotalTime)
                .requestNum(requestNum)
                .passRate(passRate)
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .commonResult(commonResult)
                .assertMessages(assertMessages)
                .build();

        statsReporter.stop();
        // 优雅关闭线程池
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return hybridSearchResult;
    }

    /**
     * 将BaseVector列表转换为RESTful API需要的JSON数组格式
     */
    private static JSONArray buildVectorDataArray(List<BaseVector> baseVectors) {
        JSONArray dataArray = new JSONArray();
        for (BaseVector baseVector : baseVectors) {
            if (baseVector instanceof FloatVec) {
                dataArray.add(baseVector.getData());
            } else if (baseVector instanceof SparseFloatVec) {
                dataArray.add(baseVector.getData());
            } else if (baseVector instanceof EmbeddedText) {
                dataArray.add(baseVector.getData());
            } else if (baseVector instanceof BinaryVec
                    || baseVector instanceof Float16Vec
                    || baseVector instanceof BFloat16Vec) {
                ByteBuffer buffer = (ByteBuffer) baseVector.getData();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                buffer.rewind();
                List<Integer> intList = new ArrayList<>();
                for (byte b : bytes) {
                    intList.add(b & 0xFF);
                }
                dataArray.add(intList);
            } else if (baseVector instanceof Int8Vec) {
                ByteBuffer buffer = (ByteBuffer) baseVector.getData();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                buffer.rewind();
                List<Integer> intList = new ArrayList<>();
                for (byte b : bytes) {
                    intList.add((int) b);
                }
                dataArray.add(intList);
            }
        }
        return dataArray;
    }

    /**
     * 静默版 HTTP POST JSON，不打印请求/响应日志，避免高并发压测时日志爆炸
     */
    private static String doPostJsonQuietly(String url, Map<String, String> headers, String json,
                                            int socketTimeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setSocketTimeout(socketTimeout > 0 ? socketTimeout : 5000)
                .setConnectionRequestTimeout(10000)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        CloseableHttpResponse response = null;
        String resultString = "";
        try {
            HttpPost httpPost = new HttpPost(url);
            if (headers != null) {
                for (String key : headers.keySet()) {
                    httpPost.setHeader(key, headers.get(key));
                }
            }
            StringEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
            httpPost.setEntity(entity);
            response = httpClient.execute(httpPost);
            resultString = EntityUtils.toString(response.getEntity(), "utf-8");
        } catch (Exception e) {
            log.error("restful request error:", e);
        } finally {
            try {
                if (response != null) {
                    response.close();
                }
                httpClient.close();
            } catch (IOException ignored) {
            }
        }
        return resultString;
    }

    @Data
    public static class RestfulHybridSearchResultInner {
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
