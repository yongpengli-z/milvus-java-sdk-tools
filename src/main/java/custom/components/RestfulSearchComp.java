package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.util.concurrent.RateLimiter;
import custom.common.CommonFunction;
import custom.entity.RestfulSearchParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SearchResultA;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.utils.MathUtil;
import custom.utils.PeriodicStatsReporter;
import io.milvus.common.utils.Float16Utils;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.data.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.config.RequestConfig;
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

@Slf4j
public class RestfulSearchComp {

    public static SearchResultA restfulSearchCollection(RestfulSearchParams searchParams) {
        Random random = new Random();
        String collection;
        if (searchParams.getCollectionRule() == null || searchParams.getCollectionRule().equalsIgnoreCase("")) {
            collection = (searchParams.getCollectionName() == null ||
                    searchParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : searchParams.getCollectionName();
        } else if (searchParams.getCollectionRule().equalsIgnoreCase("random")) {
            collection = globalCollectionNames.get(random.nextInt(globalCollectionNames.size()));
        } else if (searchParams.getCollectionRule().equalsIgnoreCase("sequence")) {
            collection = globalCollectionNames.get(searchCollectionIndex);
            searchCollectionIndex += 1;
            searchCollectionIndex = searchCollectionIndex % globalCollectionNames.size();
        } else {
            collection = (searchParams.getCollectionName() == null ||
                    searchParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : searchParams.getCollectionName();
        }

        // 判定是不是sparse向量，并且是由Function BM25生成
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collection).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.Function> functionList = collectionSchema.getFunctionList();
        boolean isUseFunction = false;
        String inputFieldName = "";
        for (CreateCollectionReq.Function function : functionList) {
            if (function.getOutputFieldNames().contains(searchParams.getAnnsField())) {
                int i = function.getOutputFieldNames().indexOf(searchParams.getAnnsField());
                inputFieldName = function.getInputFieldNames().get(i);
                log.info("inputFieldName:" + inputFieldName);
                isUseFunction = true;
                break;
            }
        }

        // 处理filter规则
        List<GeneralDataRole> generalDataRoleList = null;
        if (searchParams.getGeneralFilterRoleList() != null && searchParams.getGeneralFilterRoleList().size() > 0) {
            for (GeneralDataRole generalFilterRole : searchParams.getGeneralFilterRoleList()) {
                List<RandomRangeParams> randomRangeParamsList = generalFilterRole.getRandomRangeParamsList();
                randomRangeParamsList.sort(Comparator.comparing(RandomRangeParams::getStart));
            }
            generalDataRoleList = searchParams.getGeneralFilterRoleList().stream().filter(x -> (x.getFieldName() != null && !x.getFieldName().equalsIgnoreCase(""))).collect(Collectors.toList());
        }

        // 捞取向量数据（通过SDK query，用于构建RESTful请求体）
        List<BaseVector> searchBaseVectors;
        if (isUseFunction) {
            log.info("从collection里捞取input filed num: " + 1000);
            searchBaseVectors = CommonFunction.providerSearchFunctionData(collection, 1000, inputFieldName);
            log.info("提供给search使用的随机文本数量: " + searchBaseVectors.size());
        } else {
            log.info("从collection里捞取向量: " + 1000);
            searchBaseVectors = CommonFunction.providerSearchVectorDataset(collection, 1000, searchParams.getAnnsField());
            log.info("提供给search使用的随机向量数: " + searchBaseVectors.size());
        }

        List<BaseVector> baseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, searchParams.getNq());

        // 构建RESTful请求URL和headers
        String baseUri = newInstanceInfo.getUri();
        // 去掉末尾的斜杠
        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }
        String searchUrl = baseUri + "/v2/vectordb/entities/search";
        String token = newInstanceInfo.getToken();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }

        ArrayList<Future<RestfulSearchResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(searchParams.getNumConcurrency());

        List<String> outputs = searchParams.getOutputs();
        if (outputs != null && outputs.size() == 1 && outputs.get(0).equalsIgnoreCase("")) {
            outputs = new ArrayList<>();
        }

        float searchTotalTime;
        long startTimeTotal = System.currentTimeMillis();

        RateLimiter rateLimiter = null;
        if (searchParams.getTargetQps() > 0) {
            rateLimiter = RateLimiter.create(searchParams.getTargetQps());
            log.info("启用QPS控制: {} 请求/秒", searchParams.getTargetQps());
        }
        PeriodicStatsReporter statsReporter = new PeriodicStatsReporter("RestfulSearch");
        statsReporter.start();

        for (int c = 0; c < searchParams.getNumConcurrency(); c++) {
            int finalC = c;
            List<String> finalOutputs = outputs;
            RateLimiter finalRateLimiter = rateLimiter;
            List<GeneralDataRole> finalGeneralDataRoleList = generalDataRoleList;
            String finalCollection = collection;
            String finalSearchUrl = searchUrl;
            Callable<RestfulSearchResult> callable =
                    () -> {
                        List<BaseVector> randomBaseVectors = baseVectors;
                        log.info("线程[" + finalC + "]启动...");
                        RestfulSearchResult searchResult = new RestfulSearchResult();
                        List<Integer> returnNum = new ArrayList<>();
                        List<Float> costTime = new ArrayList<>();
                        LocalDateTime endTime = LocalDateTime.now().plusMinutes(searchParams.getRunningMinutes());
                        int requestCount = 0;
                        long lastLogTime = System.currentTimeMillis();
                        long lastPrintTime = System.currentTimeMillis();
                        while (LocalDateTime.now().isBefore(endTime)) {
                            if (finalRateLimiter != null) {
                                finalRateLimiter.acquire();
                            }
                            if (searchParams.isRandomVector()) {
                                randomBaseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, searchParams.getNq());
                            }
                            // 配置filter
                            String filter = searchParams.getFilter();
                            if (finalGeneralDataRoleList != null && finalGeneralDataRoleList.size() > 0) {
                                for (GeneralDataRole generalFilterRole : finalGeneralDataRoleList) {
                                    int replaceFilterParams;
                                    if (generalFilterRole.getSequenceOrRandom().equalsIgnoreCase("sequence")) {
                                        replaceFilterParams = CommonFunction.advanceSequenceForSearch(generalFilterRole.getRandomRangeParamsList(), searchParams.getNumConcurrency(), finalC, returnNum.size());
                                    } else {
                                        replaceFilterParams = CommonFunction.advanceRandom(generalFilterRole.getRandomRangeParamsList());
                                    }
                                    filter = filter.replaceAll("\\$" + generalFilterRole.getFieldName(), generalFilterRole.getPrefix() + replaceFilterParams);
                                }
                                if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                                    log.info("线程[" + finalC + "] restful search filter:{}", filter);
                                }
                            }

                            // 构建RESTful请求体
                            JSONObject requestBody = new JSONObject();
                            requestBody.put("collectionName", finalCollection);
                            requestBody.put("limit", searchParams.getTopK());
                            requestBody.put("annsField", searchParams.getAnnsField());

                            // 构建向量数据
                            JSONArray dataArray = buildVectorDataArray(randomBaseVectors);
                            requestBody.put("data", dataArray);

                            if (filter != null && !filter.isEmpty()) {
                                requestBody.put("filter", filter);
                            }
                            if (finalOutputs != null && !finalOutputs.isEmpty()) {
                                requestBody.put("outputFields", finalOutputs);
                            }
                            if (searchParams.getPartitionNames() != null && !searchParams.getPartitionNames().isEmpty()) {
                                requestBody.put("partitionNames", searchParams.getPartitionNames());
                            }
                            // searchParams
                            JSONObject searchParamsJson = new JSONObject();
                            searchParamsJson.put("level", searchParams.getSearchLevel() == 0 ? 1 : searchParams.getSearchLevel());
                            requestBody.put("searchParams", searchParamsJson);

                            long startItemTime = System.currentTimeMillis();
                            try {
                                String response = doPostJsonQuietly(finalSearchUrl, headers, requestBody.toJSONString(),
                                        searchParams.getSocketTimeout());
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
                                    log.error("线程[" + finalC + "] restful search error, code:{}, message:{}", code, message);
                                    if (searchParams.isIgnoreError()) {
                                        log.error("线程[" + finalC + "] Ignore error, continue search...... ");
                                    }
                                    returnNum.add(0);
                                }
                            } catch (Exception e) {
                                statsReporter.recordFailure();
                                log.error("线程[" + finalC + "] restful search error:" + e.getMessage());
                                if (searchParams.isIgnoreError()) {
                                    log.error("线程[" + finalC + "] Ignore error, continue search...... ");
                                }
                                returnNum.add(0);
                                continue;
                            }

                            if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                                log.info("线程[" + finalC + "] 已经 restful search :" + returnNum.size() + "次");
                                lastPrintTime = System.currentTimeMillis();
                            }
                            requestCount++;
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastLogTime > 5000) {
                                requestCount = 0;
                                lastLogTime = currentTime;
                            }
                        }
                        searchResult.setResultNum(returnNum);
                        searchResult.setCostTime(costTime);
                        return searchResult;
                    };
            Future<RestfulSearchResult> future = executorService.submit(callable);
            list.add(future);
        }

        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        SearchResultA searchResultA;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<RestfulSearchResult> future : list) {
            try {
                RestfulSearchResult searchResult = future.get();
                requestNum += searchResult.getResultNum().size();
                successNum += searchResult.getResultNum().stream().filter(x -> x == searchParams.getTopK()).count();
                costTimeTotal.addAll(searchResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("restful search 统计异常:" + e.getMessage());
                commonResult = CommonResult.builder()
                        .message("restful search 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                searchResultA = SearchResultA.builder().commonResult(commonResult).build();
                return searchResultA;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info("Total restful search " + requestNum + "次数 ,cost: " + searchTotalTime + " seconds! pass rate:" + (float) (100.0 * successNum / requestNum) + "%");
        log.info("Total 线程数 " + searchParams.getNumConcurrency() + " ,RPS avg :" + requestNum / searchTotalTime);
        log.info("Avg:" + MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:" + MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:" + MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:" + MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:" + MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:" + MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:" + MathUtil.calculateTP99(costTimeTotal, 0.50f));
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        double passRate = 100.0 * successNum / requestNum;
        // assertions
        List<String> assertMessages = new ArrayList<>();
        if (requestNum == 0) {
            assertMessages.add("[ASSERT FAIL] restful search requestNum == 0, no search was executed");
        }
        if (passRate < 50.0f) {
            assertMessages.add(String.format("[ASSERT FAIL] restful search passRate=%.2f%% < 50%%, %d/%d requests returned topK=%d results",
                    passRate, successNum, requestNum, searchParams.getTopK()));
        } else if (passRate < 100.0f) {
            assertMessages.add(String.format("[ASSERT WARN] restful search passRate=%.2f%% < 100%%, %d/%d requests returned topK=%d results",
                    passRate, successNum, requestNum, searchParams.getTopK()));
        }
        if (requestNum > 0 && requestNum / searchTotalTime <= 0) {
            assertMessages.add("[ASSERT FAIL] restful search RPS <= 0");
        }
        if (!assertMessages.isEmpty()) {
            log.warn("RestfulSearch assertions: " + assertMessages);
        }
        searchResultA = SearchResultA.builder()
                .rps(requestNum / searchTotalTime)
                .concurrencyNum(searchParams.getNumConcurrency())
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
        executorService.shutdown();
        return searchResultA;
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
            } else if (baseVector instanceof Float16Vec) {
                // Milvus REST API 对 Float16Vector 要求 JSON 中传 float 数组（长度=dim），
                // 由服务端内部再做 fp16 量化存储，不能直接传原始字节。
                ByteBuffer buffer = (ByteBuffer) baseVector.getData();
                dataArray.add(Float16Utils.fp16BufferToVector(buffer));
                buffer.rewind();
            } else if (baseVector instanceof BFloat16Vec) {
                // BFloat16Vector 同样需要在 REST 接口里传 float 数组。
                ByteBuffer buffer = (ByteBuffer) baseVector.getData();
                dataArray.add(Float16Utils.bf16BufferToVector(buffer));
                buffer.rewind();
            } else if (baseVector instanceof BinaryVec) {
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
    public static class RestfulSearchResult {
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
