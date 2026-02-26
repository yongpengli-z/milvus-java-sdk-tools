package custom.components;

import com.google.common.collect.Lists;
import custom.common.CommonFunction;
import custom.entity.RecallParams;
import custom.entity.result.CommonResult;
import custom.entity.result.RecallResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static custom.BaseTest.*;

@Slf4j
public class RecallComp {

    public static RecallResult recallTest(RecallParams params) {
        long totalStartTime = System.currentTimeMillis();
        try {
            // Step 1: 确定 collection 名称
            String collection = (params.getCollectionName() == null
                    || params.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                    : params.getCollectionName();
            log.info("RecallTest collection: {}", collection);

            // Step 2: 应用默认值
            int sampleNum = params.getSampleNum() <= 0 ? 1000 : params.getSampleNum();
            int nq = params.getNq() <= 0 ? 10 : params.getNq();
            int groundTruthLevel = params.getGroundTruthLevel() <= 0 ? 10 : params.getGroundTruthLevel();
            List<Integer> topKList = (params.getTopKList() == null || params.getTopKList().isEmpty())
                    ? Lists.newArrayList(1) : params.getTopKList();
            List<Integer> searchLevelList = (params.getSearchLevelList() == null || params.getSearchLevelList().isEmpty())
                    ? Lists.newArrayList(1) : params.getSearchLevelList();
            String consistencyLevelStr = (params.getConsistencyLevel() == null || params.getConsistencyLevel().isEmpty())
                    ? "BOUNDED" : params.getConsistencyLevel();
            ConsistencyLevel consistencyLevel = ConsistencyLevel.valueOf(consistencyLevelStr);
            String filter = params.getFilter();
            String annsField = (params.getAnnsField() == null || params.getAnnsField().isEmpty())
                    ? "FloatVector_1" : params.getAnnsField();

            // Step 3: 从 collection 采样向量
            log.info("从 collection [{}] 采样 {} 条向量, annsField [{}]", collection, sampleNum, annsField);
            List<BaseVector> allSampledVectors = CommonFunction.providerSearchVectorDataset(collection, sampleNum, annsField);
            log.info("实际采样向量数: {}", allSampledVectors.size());

            if (allSampledVectors.isEmpty()) {
                CommonResult commonResult = CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message("采样向量为空，collection 可能无数据或 annsField 不正确")
                        .build();
                return RecallResult.builder()
                        .commonResult(commonResult)
                        .nq(nq)
                        .sampleNum(0)
                        .build();
            }

            // Step 4: 选取 nq 个查询向量
            nq = Math.min(nq, allSampledVectors.size());
            List<BaseVector> queryVectors = CommonFunction.providerSearchVectorByNq(allSampledVectors, nq);
            log.info("选取 {} 个查询向量用于 recall 评测", queryVectors.size());

            // Step 5: 用 max(topKList) + groundTruthLevel 暴力搜索获取 ground truth
            int maxTopK = Collections.max(topKList);
            Map<String, Object> groundTruthSearchParams = new HashMap<>();
            groundTruthSearchParams.put("level", groundTruthLevel);

            log.info("计算 ground truth: topK={}, level={}", maxTopK, groundTruthLevel);
            long gtStartTime = System.currentTimeMillis();

            SearchReq.SearchReqBuilder gtReqBuilder = SearchReq.builder()
                    .collectionName(collection)
                    .topK(maxTopK)
                    .consistencyLevel(consistencyLevel)
                    .data(queryVectors)
                    .searchParams(groundTruthSearchParams)
                    .annsField(annsField);
            if (filter != null && !filter.isEmpty()) {
                gtReqBuilder.filter(filter);
            }
            SearchResp gtResp = milvusClientV2.search(gtReqBuilder.build());
            long gtEndTime = System.currentTimeMillis();
            log.info("Ground truth 搜索耗时: {} ms", (gtEndTime - gtStartTime));

            // 解析 ground truth: 每个查询向量对应的 top-maxTopK ID 列表
            List<List<SearchResp.SearchResult>> gtResults = gtResp.getSearchResults();
            List<List<Object>> groundTruthIds = new ArrayList<>();
            for (List<SearchResp.SearchResult> singleQueryResults : gtResults) {
                List<Object> ids = new ArrayList<>();
                for (SearchResp.SearchResult sr : singleQueryResults) {
                    ids.add(sr.getId());
                }
                groundTruthIds.add(ids);
            }

            // Step 6: 遍历每个 (topK, searchLevel) 组合，搜索并计算 recall
            List<RecallResult.RecallDetail> recallDetails = new ArrayList<>();

            for (int topK : topKList) {
                for (int searchLevel : searchLevelList) {
                    log.info("评测: topK={}, searchLevel={}", topK, searchLevel);

                    Map<String, Object> searchParamsMap = new HashMap<>();
                    searchParamsMap.put("level", searchLevel);

                    long searchStartTime = System.currentTimeMillis();

                    SearchReq.SearchReqBuilder reqBuilder = SearchReq.builder()
                            .collectionName(collection)
                            .topK(topK)
                            .consistencyLevel(consistencyLevel)
                            .data(queryVectors)
                            .searchParams(searchParamsMap)
                            .annsField(annsField);
                    if (filter != null && !filter.isEmpty()) {
                        reqBuilder.filter(filter);
                    }
                    SearchResp searchResp = milvusClientV2.search(reqBuilder.build());

                    long searchEndTime = System.currentTimeMillis();
                    double avgLatencyMs = (double) (searchEndTime - searchStartTime) / nq;

                    // 计算 recall@K
                    List<List<SearchResp.SearchResult>> testResults = searchResp.getSearchResults();
                    int totalIntersection = 0;
                    int totalExpected = 0;

                    for (int qi = 0; qi < Math.min(testResults.size(), groundTruthIds.size()); qi++) {
                        // ground truth 中前 topK 个 ID
                        List<Object> gtIds = groundTruthIds.get(qi);
                        Set<String> gtIdSet = new HashSet<>();
                        for (int k = 0; k < Math.min(topK, gtIds.size()); k++) {
                            gtIdSet.add(gtIds.get(k).toString());
                        }

                        // 搜索结果 ID
                        List<SearchResp.SearchResult> testQueryResults = testResults.get(qi);
                        Set<String> testIdSet = new HashSet<>();
                        for (SearchResp.SearchResult sr : testQueryResults) {
                            testIdSet.add(sr.getId().toString());
                        }

                        // 交集
                        Set<String> intersection = new HashSet<>(gtIdSet);
                        intersection.retainAll(testIdSet);
                        totalIntersection += intersection.size();
                        totalExpected += Math.min(topK, gtIds.size());
                    }

                    double recall = totalExpected > 0
                            ? (double) totalIntersection / totalExpected
                            : 0.0;

                    String formattedRecall = String.format("%.6f", recall);
                    log.info("topK={}, searchLevel={}, recall={}, avgLatencyMs={}", topK, searchLevel, formattedRecall, String.format("%.2f", avgLatencyMs));

                    recallDetails.add(RecallResult.RecallDetail.builder()
                            .topK(topK)
                            .searchLevel(searchLevel)
                            .recall(recall)
                            .avgLatencyMs(avgLatencyMs)
                            .build());
                }
            }

            // Step 7: 输出召回率对比矩阵
            logRecallMatrix(topKList, searchLevelList, recallDetails);

            // Step 8: 返回结果
            long totalEndTime = System.currentTimeMillis();
            double totalCostSeconds = (totalEndTime - totalStartTime) / 1000.0;

            CommonResult commonResult = CommonResult.builder()
                    .result(ResultEnum.SUCCESS.result)
                    .build();

            return RecallResult.builder()
                    .commonResult(commonResult)
                    .nq(nq)
                    .sampleNum(allSampledVectors.size())
                    .groundTruthLevel(groundTruthLevel)
                    .totalCostSeconds(totalCostSeconds)
                    .recallDetails(recallDetails)
                    .build();

        } catch (Exception e) {
            log.error("RecallTest 异常: {}", e.getMessage(), e);
            long totalEndTime = System.currentTimeMillis();
            CommonResult commonResult = CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message("RecallTest 异常: " + e.getMessage())
                    .build();
            return RecallResult.builder()
                    .commonResult(commonResult)
                    .totalCostSeconds((totalEndTime - totalStartTime) / 1000.0)
                    .build();
        }
    }

    /**
     * 输出格式化的 recall 对比矩阵到日志
     */
    private static void logRecallMatrix(List<Integer> topKList, List<Integer> searchLevelList,
                                        List<RecallResult.RecallDetail> details) {
        Map<String, RecallResult.RecallDetail> lookup = new HashMap<>();
        for (RecallResult.RecallDetail d : details) {
            lookup.put(d.getTopK() + "_" + d.getSearchLevel(), d);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== Recall Comparison Matrix ==========\n");

        // 表头
        sb.append(String.format("%-12s", "topK\\level"));
        for (int level : searchLevelList) {
            sb.append(String.format("%-20s", "level=" + level));
        }
        sb.append("\n");

        // 数据行
        for (int topK : topKList) {
            sb.append(String.format("%-12s", "topK=" + topK));
            for (int level : searchLevelList) {
                RecallResult.RecallDetail d = lookup.get(topK + "_" + level);
                if (d != null) {
                    sb.append(String.format("%-20s", String.format("%.4f (%.1fms)", d.getRecall(), d.getAvgLatencyMs())));
                } else {
                    sb.append(String.format("%-20s", "N/A"));
                }
            }
            sb.append("\n");
        }
        sb.append("===============================================");
        log.info(sb.toString());
    }
}
