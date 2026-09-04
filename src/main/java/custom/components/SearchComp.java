package custom.components;

import com.google.common.util.concurrent.RateLimiter;
import custom.common.CommonFunction;
import custom.common.QueryDatasetEnum;
import custom.entity.SearchParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SearchResultA;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.utils.MathUtil;
import custom.utils.PeriodicStatsReporter;
import custom.utils.QueryDatasetUtil;
import io.milvus.v2.client.RetryConfig;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import io.milvus.v2.client.MilvusClientV2;

import static custom.BaseTest.*;

@Slf4j
public class SearchComp {
    public static SearchResultA searchCollection(SearchParams searchParams) {
        // 根据 targetEndpoint 选择 client
        MilvusClientV2 client = getMilvusClient(searchParams.getTargetEndpoint());
        log.info("Search 使用 endpoint: {}", describeTargetEndpoint(searchParams.getTargetEndpoint()));

        // 先search collection
        // 判断collection获取规则（支持 collectionNamePrefix 前缀过滤后再 sequence/random）
        boolean perRequestCollection = "sequence_per_request".equalsIgnoreCase(
                searchParams.getCollectionRule() == null ? "" : searchParams.getCollectionRule());
        List<String> collectionPool = null;
        String collection;
        if (perRequestCollection) {
            // 逐请求轮转：步骤启动时只解析池子，每个请求从池子取下一个 collection（全局原子游标，跨线程唯一）
            collectionPool = CommonFunction.resolveSearchCollectionPool(searchParams.getCollectionNamePrefix(),
                    searchParams.getCollectionRangeStart(), searchParams.getCollectionRangeEnd());
            // schema 检测与底库捞取向量都以池子第一个 collection 为基准（假设池内 collection 同构）
            collection = collectionPool.get(0);
            log.info("Search sequence_per_request 模式，collection池: {} 个，基准collection: {}", collectionPool.size(), collection);
        } else {
            collection = CommonFunction.resolveSearchCollection(
                    searchParams.getCollectionRule(), searchParams.getCollectionName(), searchParams.getCollectionNamePrefix(),
                    searchParams.getCollectionRangeStart(), searchParams.getCollectionRangeEnd());
            log.info("Search 目标 collection: {}", collection);
        }

        // 判定是不是sparse向量，并且是由Function BM25生成
        DescribeCollectionResp describeCollectionResp = client.describeCollection(DescribeCollectionReq.builder().collectionName(collection).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        // 获取function列表，查找不需要构建数据的 outputFieldNames
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
        //先处理search里数据生成的规则，先进行排序处理
        List<GeneralDataRole> generalDataRoleList = null;
        if (searchParams.getGeneralFilterRoleList() != null && searchParams.getGeneralFilterRoleList().size() > 0) {
            for (GeneralDataRole generalFilterRole : searchParams.getGeneralFilterRoleList()) {
                List<RandomRangeParams> randomRangeParamsList = generalFilterRole.getRandomRangeParamsList();
                randomRangeParamsList.sort(Comparator.comparing(RandomRangeParams::getStart));
            }
            generalDataRoleList = searchParams.getGeneralFilterRoleList().stream().filter(x -> (x.getFieldName() != null && !x.getFieldName().equalsIgnoreCase(""))).collect(Collectors.toList());

        }
        List<BaseVector> searchBaseVectors;
        QueryDatasetEnum queryDatasetEnum = QueryDatasetEnum.fromName(searchParams.getQueryDataset());
        if (searchParams.getQueryDataset() != null && !searchParams.getQueryDataset().equalsIgnoreCase("") && queryDatasetEnum == null) {
            log.warn("queryDataset={} 未匹配到 QueryDatasetEnum，回退为从collection里捞取查询输入", searchParams.getQueryDataset());
        }
        if (queryDatasetEnum != null) {
            // 指定了 query 数据集：不从底库捞，全量加载数据集文件作为查询输入
            log.info("使用query数据集 {} 全量加载search查询输入", queryDatasetEnum.datasetName);
            searchBaseVectors = QueryDatasetUtil.providerAllQueryVectors(queryDatasetEnum);
            log.info("提供给search使用的查询输入数量: " + searchBaseVectors.size());
        } else if (isUseFunction) {
            log.info("从collection里捞取input filed num: " + 1000);
            searchBaseVectors = CommonFunction.providerSearchFunctionData(client, collection, 1000, inputFieldName);
            log.info("提供给search使用的随机文本数量: " + searchBaseVectors.size());
        } else {
            // 随机向量，从数据库里筛选--暂定1000条
            log.info("从collection里捞取向量: " + 1000);
            searchBaseVectors = CommonFunction.providerSearchVectorDataset(client, collection, 1000, searchParams.getAnnsField());
            log.info("提供给search使用的随机向量数: " + searchBaseVectors.size());
        }

        // 如果不随机，则随机一个
        List<BaseVector> baseVectors = CommonFunction.providerSearchVectorByNq(searchBaseVectors, searchParams.getNq());

        ArrayList<Future<SearchResult>> list = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(searchParams.getNumConcurrency());
        // 处理output
        List<String> outputs = searchParams.getOutputs();
        if (outputs.size() == 1 && outputs.get(0).equalsIgnoreCase("")) {
            outputs = new ArrayList<>();
        }

        float searchTotalTime;
        long startTimeTotal = System.currentTimeMillis();
        Map<String, Object> searchLevel = new HashMap<>();
        searchLevel.put("level", searchParams.getSearchLevel() == 0 ? 1 : searchParams.getSearchLevel());
        if (searchParams.getIndexAlgo() != null && !searchParams.getIndexAlgo().equalsIgnoreCase("")) {
            searchLevel.put("index_algo", searchParams.getIndexAlgo());
        }
        // 1. 创建RateLimiter实例（根据配置的QPS）
        RateLimiter rateLimiter = null;
        if (searchParams.getTargetQps() > 0) {
            rateLimiter = RateLimiter.create(searchParams.getTargetQps());
            log.info("启用QPS控制: {} 请求/秒", searchParams.getTargetQps());
        }
        PeriodicStatsReporter statsReporter = new PeriodicStatsReporter("Search");
        statsReporter.start();
        for (int c = 0; c < searchParams.getNumConcurrency(); c++) {
            int finalC = c;
            List<String> finalOutputs = outputs;
            RateLimiter finalRateLimiter = rateLimiter;
            List<GeneralDataRole> finalGeneralDataRoleList = generalDataRoleList;
            String finalCollection = collection;
            List<String> finalCollectionPool = collectionPool;
            Callable<SearchResult> callable =
                    () -> {
                        List<BaseVector> randomBaseVectors = baseVectors;
                        log.info("线程[" + finalC + "]启动...");
                        SearchResult searchResult = new SearchResult();
                        List<Integer> returnNum = new ArrayList<>();
                        List<Float> costTime = new ArrayList<>();
                        LocalDateTime endTime = LocalDateTime.now().plusMinutes(searchParams.getRunningMinutes());
                        // 次数模式：runningCount > 0 时每线程跑满 N 次后停止（次数优先，不再看时间）
                        long runningCount = searchParams.getRunningCount();
                        boolean countMode = runningCount > 0;
                        if (countMode) {
                            log.info("线程[" + finalC + "]按次数模式运行: " + runningCount + " 次");
                        }
                        // QPS控制计数器
                        int requestCount = 0;
                        long lastLogTime = System.currentTimeMillis();
                        long lastPrintTime = System.currentTimeMillis();
                        while (countMode ? returnNum.size() < runningCount : LocalDateTime.now().isBefore(endTime)) {
                            // 3. QPS控制点（如果需要）
                            if (finalRateLimiter != null) {
                                finalRateLimiter.acquire(); // 阻塞直到获得令牌
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
                                    log.debug("search random:{}", replaceFilterParams);
                                    filter = CommonFunction.replaceFilterPlaceholder(filter, generalFilterRole, replaceFilterParams);
                                }
                                if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                                    log.info("线程[" + finalC + "] search filter:{}", filter);
                                }
                            }
                            // sequence_per_request 模式：每个请求取池子里下一个 collection（全局游标，跨线程唯一）
                            String currentCollection = finalCollectionPool != null
                                    ? CommonFunction.nextCollectionPerRequest(finalCollectionPool)
                                    : finalCollection;
                            SearchReq searchReq = SearchReq.builder()
                                    .topK(searchParams.getTopK())
                                    .outputFields(finalOutputs)
                                    .consistencyLevel(ConsistencyLevel.BOUNDED)
                                    .collectionName(currentCollection)
                                    .searchParams(searchLevel)
                                    .filter(filter)
                                    .data(randomBaseVectors)
                                    .annsField(searchParams.getAnnsField())
                                    .partitionNames(searchParams.getPartitionNames() == null || searchParams.getPartitionNames().isEmpty() ? new ArrayList<>() : searchParams.getPartitionNames())
                                    .build();
                            long startItemTime = System.currentTimeMillis();
                            SearchResp search = null;
                            try {
                                long timeoutMs = searchParams.getTimeout() > 0 ? searchParams.getTimeout() : 800;
                                search = client.withTimeout(timeoutMs,TimeUnit.MILLISECONDS).withRetry(RetryConfig.builder().maxRetryTimes(1).build()).search(searchReq);
                            } catch (Exception e) {
                                statsReporter.recordFailure();
                                log.error("线程[" + finalC + "]  search error :" + e.getMessage());
                                if (searchParams.isIgnoreError()) {
                                    log.error("线程[" + finalC + "] Ignore error, continue search...... ");
                                }
                                returnNum.add(0);
                                continue;
                            }
                            long endItemTime = System.currentTimeMillis();
                            float costTimeItem = (float) ((endItemTime - startItemTime) / 1000.00);
                            log.debug("线程[" + finalC + "]  search cost:" + costTimeItem + " s" + "，collection：" + currentCollection + "，result size：" + search.getSearchResults().size() + ",");
                            // sequence_per_request 模式：每次 search 打印使用的 collection 及耗时
                            if (finalCollectionPool != null) {
                                log.info("线程[" + finalC + "] search collection: " + currentCollection + "，cost: " + costTimeItem + " s");
                            }
                            costTime.add(costTimeItem);
                            statsReporter.recordCostTime(costTimeItem);
//                            returnNum.add(search.getSearchResults().get(0).size());
                            // getSearchResults() 返回 List<List<SearchResult>>，外层size=nq，内层size=每个query的结果数
                            // 取第一个查询向量的结果数量来判断是否返回了topK条结果
                            if (search == null || search.getSearchResults().isEmpty()) {
                                returnNum.add(0);
                            } else {
                                returnNum.add(search.getSearchResults().get(0).size());
                            }
                            if (System.currentTimeMillis() - lastPrintTime >= 60000) {
                                log.info("线程[" + finalC + "] 已经 search :" + returnNum.size() + "次");
                                lastPrintTime = System.currentTimeMillis();
                            }
                            // 4. QPS监控日志
                            requestCount++;
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastLogTime > 5000) { // 每5秒打印一次QPS
                                double actualQps = requestCount / ((currentTime - lastLogTime) / 1000.0);
                                log.debug("线程[{}] 当前QPS: {}", finalC, actualQps);
                                requestCount = 0;
                                lastLogTime = currentTime;
                            }
                        }
                        searchResult.setResultNum(returnNum);
                        searchResult.setCostTime(costTime);
                        return searchResult;
                    };
            Future<SearchResult> future = executorService.submit(callable);
            list.add(future);
        }
        long requestNum = 0;
        long successNum = 0;
        CommonResult commonResult;
        SearchResultA searchResultA;
        List<Float> costTimeTotal = new ArrayList<>();
        for (Future<SearchResult> future : list) {
            try {
                SearchResult searchResult = future.get();
                requestNum += searchResult.getResultNum().size();
                successNum += searchResult.getResultNum().stream().filter(x -> x == searchParams.getTopK()).count();
                costTimeTotal.addAll(searchResult.getCostTime());
            } catch (InterruptedException | ExecutionException e) {
                log.error("search 统计异常:" + e.getMessage());
                commonResult = CommonResult.builder()
                        .message("search 统计异常:" + e.getMessage())
                        .result(ResultEnum.EXCEPTION.result)
                        .build();
                searchResultA = SearchResultA.builder().commonResult(commonResult).build();
                return searchResultA;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        searchTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info(
                "Total search " + requestNum + "次数 ,cost: " + searchTotalTime + " seconds! pass rate:" + (float) (100.0 * successNum / requestNum) + "%");
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
            assertMessages.add("[ASSERT FAIL] search requestNum == 0, no search was executed");
        }
        if (passRate < 50.0f) {
            assertMessages.add(String.format("[ASSERT FAIL] search passRate=%.2f%% < 50%%, %d/%d requests returned topK=%d results",
                    passRate, successNum, requestNum, searchParams.getTopK()));
        } else if (passRate < 100.0f) {
            assertMessages.add(String.format("[ASSERT WARN] search passRate=%.2f%% < 100%%, %d/%d requests returned topK=%d results",
                    passRate, successNum, requestNum, searchParams.getTopK()));
        }
        if (requestNum > 0 && requestNum / searchTotalTime <= 0) {
            assertMessages.add("[ASSERT FAIL] search RPS <= 0");
        }
        if (!assertMessages.isEmpty()) {
            log.warn("Search assertions: " + assertMessages);
        }
        CommonResult.markWarningIfAssertFail(commonResult, assertMessages);
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

    @Data
    public static class SearchResult {
        private List<Float> costTime;
        private List<Integer> resultNum;
    }
}
