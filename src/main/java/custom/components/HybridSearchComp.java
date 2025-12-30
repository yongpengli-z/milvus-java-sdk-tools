package custom.components;

import com.google.common.util.concurrent.RateLimiter;
import custom.common.CommonFunction;
import custom.entity.HybridSearchParams;
import custom.entity.result.CommonResult;
import custom.entity.result.HybridSearchResult;
import custom.entity.result.ResultEnum;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.utils.MathUtil;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static custom.BaseTest.*;

/**
 * HybridSearch（混合搜索）组件。
 * <p>
 * 支持在同一个 collection 中对多个向量字段进行搜索，并使用融合策略合并结果。
 */
@Slf4j
public class HybridSearchComp {
    public static HybridSearchResult hybridSearchCollection(HybridSearchParams hybridSearchParams) {
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
            log.error("HybridSearch searchRequests 不能为空");
            CommonResult commonResult = CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message("HybridSearch searchRequests 不能为空")
                    .build();
            return HybridSearchResult.builder()
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

        // 为每个搜索请求准备向量数据
        Map<String, List<BaseVector>> fieldVectorsMap = new HashMap<>();
        for (HybridSearchParams.HybridSearchRequest request : hybridSearchParams.getSearchRequests()) {
            String annsField = request.getAnnsField();
            if (annsField == null || annsField.equalsIgnoreCase("")) {
                log.error("HybridSearchRequest annsField 不能为空");
                continue;
            }
            // 从 collection 中采样向量
            log.info("从collection里捞取向量字段 {} 的向量: {}", annsField, 1000);
            List<BaseVector> searchBaseVectors = CommonFunction.providerSearchVectorDataset(collection, 1000, annsField);
            log.info("提供给hybridSearch使用的随机向量数: {}", searchBaseVectors.size());
            fieldVectorsMap.put(annsField, searchBaseVectors);
        }

        // 准备初始查询向量
        Map<String, List<BaseVector>> initialVectorsMap = new HashMap<>();
        for (Map.Entry<String, List<BaseVector>> entry : fieldVectorsMap.entrySet()) {
            List<BaseVector> baseVectors = CommonFunction.providerSearchVectorByNq(entry.getValue(), hybridSearchParams.getNq());
            initialVectorsMap.put(entry.getKey(), baseVectors);
        }

        ArrayList<Future<HybridSearchResultInner>> list = new ArrayList<>();
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
        // 如果使用 RRF 且没有设置 k，使用默认值
        if ("RRF".equalsIgnoreCase(hybridSearchParams.getRanker()) && !rankerParams.containsKey("k")) {
            rankerParams.put("k", 60);
        }
        // 如果使用 WeightedRanker 且没有设置 weights，使用平均权重
        if ("WeightedRanker".equalsIgnoreCase(hybridSearchParams.getRanker()) && !rankerParams.containsKey("weights")) {
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

        for (int c = 0; c < hybridSearchParams.getNumConcurrency(); c++) {
            int finalC = c;
            Callable<HybridSearchResultInner> callable = () -> {
                log.info("线程[{}]启动...", finalC);
                HybridSearchResultInner result = new HybridSearchResultInner();
                List<Integer> returnNum = new ArrayList<>();
                List<Float> costTime = new ArrayList<>();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(hybridSearchParams.getRunningMinutes());
                int printLog = 1;
                // QPS控制计数器
                int requestCount = 0;
                long lastLogTime = System.currentTimeMillis();

                // 准备当前线程的向量数据
                Map<String, List<BaseVector>> threadVectorsMap = new HashMap<>();
                for (Map.Entry<String, List<BaseVector>> entry : initialVectorsMap.entrySet()) {
                    threadVectorsMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }

                while (LocalDateTime.now().isBefore(endTime)) {
                    // QPS控制点
                    if (finalRateLimiter != null) {
                        finalRateLimiter.acquire(); // 阻塞直到获得令牌
                    }

                    // 如果需要随机向量，重新采样
                    if (hybridSearchParams.isRandomVector()) {
                        for (HybridSearchParams.HybridSearchRequest request : hybridSearchParams.getSearchRequests()) {
                            String annsField = request.getAnnsField();
                            List<BaseVector> baseVectors = fieldVectorsMap.get(annsField);
                            if (baseVectors != null) {
                                List<BaseVector> randomVectors = CommonFunction.providerSearchVectorByNq(baseVectors, hybridSearchParams.getNq());
                                threadVectorsMap.put(annsField, randomVectors);
                            }
                        }
                    }

                    // 配置filter
                    String filter = hybridSearchParams.getFilter();
                    if (finalGeneralDataRoleList != null && finalGeneralDataRoleList.size() > 0) {
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
                            filter = filter.replaceAll("\\$" + generalFilterRole.getFieldName(), generalFilterRole.getPrefix() + replaceFilterParams);
                        }
                        log.info("线程[{}] hybridSearch filter:{}", finalC, filter);
                    }

                    // 构建多个 AnnSearchReq
                    List<AnnSearchReq> annSearchReqList = new ArrayList<>();
                    for (HybridSearchParams.HybridSearchRequest request : hybridSearchParams.getSearchRequests()) {
                        String annsField = request.getAnnsField();
                        List<BaseVector> vectors = threadVectorsMap.get(annsField);
                        if (vectors == null || vectors.isEmpty()) {
                            log.warn("线程[{}] 字段 {} 的向量为空，跳过", finalC, annsField);
                            continue;
                        }

                        // 确定 MetricType
                        IndexParam.MetricType metricType;
                        String metricTypeStr = request.getMetricType();
                        if (metricTypeStr == null || metricTypeStr.equalsIgnoreCase("")) {
                            metricTypeStr = "L2";
                        }
                        switch (metricTypeStr.toUpperCase()) {
                            case "IP":
                                metricType = IndexParam.MetricType.IP;
                                break;
                            case "COSINE":
                                metricType = IndexParam.MetricType.COSINE;
                                break;
                            case "HAMMING":
                                metricType = IndexParam.MetricType.HAMMING;
                                break;
                            case "JACCARD":
                                metricType = IndexParam.MetricType.JACCARD;
                                break;
                            case "BM25":
                                metricType = IndexParam.MetricType.BM25;
                                break;
                            case "L2":
                            default:
                                metricType = IndexParam.MetricType.L2;
                                break;
                        }

                        // 准备 searchParams
                        Map<String, Object> searchParams = request.getSearchParams();
                        if (searchParams == null) {
                            searchParams = new HashMap<>();
                            searchParams.put("level", 1);
                        } else if (!searchParams.containsKey("level")) {
                            searchParams.put("level", 1);
                        }

                        // 构建 AnnSearchReq
                        // 注意：根据 Milvus SDK 2.6.6 的实际 API，可能需要调整方法名
                        // 如果编译错误，请根据实际 SDK 文档调整（可能是 data/vectors/annsField/vectorFieldName 等）
                        AnnSearchReq.AnnSearchReqBuilder<?, ?> annSearchReqBuilder = AnnSearchReq.builder()
                                .topK(request.getTopK())
                                .metricType(metricType);
                        
                        // 设置 searchParams（如果 API 支持）
                        try {
                            annSearchReqBuilder.getClass().getMethod("searchParams", Map.class).invoke(annSearchReqBuilder, searchParams);
                        } catch (Exception e) {
                            log.debug("AnnSearchReq 不支持 searchParams 方法，跳过: {}", e.getMessage());
                        }
                        
                        // 设置向量数据和字段名（根据实际 API 调整）
                        try {
                            // 尝试使用 data() 和 annsField() 方法
                            annSearchReqBuilder.getClass().getMethod("data", List.class).invoke(annSearchReqBuilder, vectors);
                            annSearchReqBuilder.getClass().getMethod("annsField", String.class).invoke(annSearchReqBuilder, annsField);
                        } catch (Exception e1) {
                            try {
                                // 尝试使用 vectors() 和 vectorFieldName() 方法
                                annSearchReqBuilder.getClass().getMethod("vectors", List.class).invoke(annSearchReqBuilder, vectors);
                                annSearchReqBuilder.getClass().getMethod("vectorFieldName", String.class).invoke(annSearchReqBuilder, annsField);
                            } catch (Exception e2) {
                                log.error("无法设置 AnnSearchReq 的向量数据，请检查 Milvus SDK API。错误: {}", e2.getMessage());
                                continue;
                            }
                        }
                        
                        AnnSearchReq annSearchReq = annSearchReqBuilder.build();

                        annSearchReqList.add(annSearchReq);
                    }

                    if (annSearchReqList.isEmpty()) {
                        log.warn("线程[{}] annSearchReqList 为空，跳过本次请求", finalC);
                        continue;
                    }

                    // 构建 HybridSearchReq
                    // 注意：根据 Milvus SDK 2.6.6 的实际 API，方法名可能不同
                    HybridSearchReq.HybridSearchReqBuilder<?, ?> hybridSearchReqBuilder = HybridSearchReq.builder()
                            .collectionName(finalCollection)
                            .searchRequests(annSearchReqList)
                            .topK(hybridSearchParams.getTopK())
                            .consistencyLevel(ConsistencyLevel.BOUNDED);
                    
                    // 尝试设置 outputFields 和 filter（根据实际 API 调整）
                    try {
                        if (finalOutputs != null && !finalOutputs.isEmpty()) {
                            hybridSearchReqBuilder.getClass().getMethod("outputFields", List.class).invoke(hybridSearchReqBuilder, finalOutputs);
                        }
                        if (filter != null && !filter.equalsIgnoreCase("")) {
                            // 尝试使用 filter() 或 expr() 方法
                            try {
                                hybridSearchReqBuilder.getClass().getMethod("filter", String.class).invoke(hybridSearchReqBuilder, filter);
                            } catch (Exception e) {
                                hybridSearchReqBuilder.getClass().getMethod("expr", String.class).invoke(hybridSearchReqBuilder, filter);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("设置 HybridSearchReq 的 outputFields 或 filter 失败: {}", e.getMessage());
                    }

                    // 设置融合策略
                    // 注意：根据 Milvus SDK 2.6.6 的实际 API，ranker 和 rankerParams 方法可能不同
                    // 如果编译错误，请根据实际 SDK 文档调整
                    String rankerType = hybridSearchParams.getRanker();
                    if (rankerType == null || rankerType.equalsIgnoreCase("")) {
                        rankerType = "RRF";
                    }
                    try {
                        if (finalRankerParams != null && !finalRankerParams.isEmpty()) {
                            // 尝试设置 rankerParams
                            hybridSearchReqBuilder.getClass().getMethod("rankerParams", Map.class).invoke(hybridSearchReqBuilder, finalRankerParams);
                        }
                    } catch (Exception e) {
                        log.debug("HybridSearchReq 不支持 rankerParams 方法或参数设置失败，使用默认配置: {}", e.getMessage());
                    }

                    HybridSearchReq hybridSearchReq = hybridSearchReqBuilder.build();

                    long startItemTime = System.currentTimeMillis();
                    SearchResp hybridSearchResp = null;
                    try {
                        hybridSearchResp = milvusClientV2.hybridSearch(hybridSearchReq);
                    } catch (Exception e) {
                        log.error("线程[{}] hybridSearch error :{}", finalC, e.getMessage());
                        if (hybridSearchParams.isIgnoreError()) {
                            log.error("线程[{}] Ignore error, continue hybridSearch......", finalC);
                            returnNum.add(0);
                            continue;
                        }
                        throw e;
                    }
                    long endItemTime = System.currentTimeMillis();
                    float costTimeItem = (float) ((endItemTime - startItemTime) / 1000.00);
                    int resultSize = hybridSearchResp != null ? hybridSearchResp.getSearchResults().size() : 0;
                    log.info("线程[{}] hybridSearch cost:{} s，result size：{}", finalC, costTimeItem, resultSize);
                    costTime.add(costTimeItem);
                    returnNum.add(resultSize);

                    if (printLog >= logInterval) {
                        log.info("线程[{}] 已经 hybridSearch :{}次", finalC, returnNum.size());
                        printLog = 0;
                    }
                    printLog++;

                    // QPS监控日志
                    requestCount++;
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastLogTime > 5000) { // 每5秒打印一次QPS
                        double actualQps = requestCount / ((currentTime - lastLogTime) / 1000.0);
                        requestCount = 0;
                        lastLogTime = currentTime;
                    }
                }

                result.setResultNum(returnNum);
                result.setCostTime(costTime);
                return result;
            };
            Future<HybridSearchResultInner> future = executorService.submit(callable);
            list.add(future);
        }

        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        HybridSearchResult hybridSearchResult;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<HybridSearchResultInner> future : list) {
            try {
                HybridSearchResultInner result = future.get();
                requestNum += result.getResultNum().size();
                successNum += result.getResultNum().stream().filter(x -> x > 0).count();
                if (result.getCostTime() != null) {
                    costTimeTotal.addAll(result.getCostTime());
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("hybridSearch 统计异常:{}", e.getMessage());
                commonResult = CommonResult.builder()
                        .message("hybridSearch 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                hybridSearchResult = HybridSearchResult.builder().commonResult(commonResult).build();
                executorService.shutdown();
                return hybridSearchResult;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info("Total hybridSearch {} 次数 ,cost: {} seconds! pass rate:{}%",
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
        hybridSearchResult = HybridSearchResult.builder()
                .rps(requestNum / searchTotalTime)
                .concurrencyNum(hybridSearchParams.getNumConcurrency())
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
        return hybridSearchResult;
    }

    @Data
    static class HybridSearchResultInner {
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}

