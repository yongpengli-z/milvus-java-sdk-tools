package custom.components;

import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import custom.common.DatasetEnum;
import custom.entity.UpsertParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UpsertResult;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.utils.DatasetUtil;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static custom.BaseTest.*;
import static custom.BaseTest.queryCollectionIndex;

@Slf4j
public class UpsertComp {
    public static UpsertResult upsertCollection(UpsertParams upsertParams) {
        // 先search collection
        // 判断collection获取规则
        String collectionName = "";
        Random random = new Random();
        if (upsertParams.getCollectionRule() == null || upsertParams.getCollectionRule().equalsIgnoreCase("")) {
            collectionName = (upsertParams.getCollectionName() == null ||
                    upsertParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : upsertParams.getCollectionName();
        } else if (upsertParams.getCollectionRule().equalsIgnoreCase("random")) {
            collectionName = globalCollectionNames.get(random.nextInt(globalCollectionNames.size()));
        } else if (upsertParams.getCollectionRule().equalsIgnoreCase("sequence")) {
            collectionName = globalCollectionNames.get(upsertCollectionIndex);
            upsertCollectionIndex += 1;
            upsertCollectionIndex = upsertCollectionIndex % globalCollectionNames.size();
        } else {
            collectionName = (upsertParams.getCollectionName() == null ||
                    upsertParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : upsertParams.getCollectionName();
        }


        DatasetEnum datasetEnum;
        List<String> fileNames = new ArrayList<>();
        List<Long> fileSizeList = new ArrayList<>();
        //先处理upsert里数据生成的规则，先进行排序处理
        if (upsertParams.getGeneralDataRoleList() != null && upsertParams.getGeneralDataRoleList().size() > 0) {
            for (GeneralDataRole generalDataRole : upsertParams.getGeneralDataRoleList()) {
                List<RandomRangeParams> randomRangeParamsList = generalDataRole.getRandomRangeParamsList();
                randomRangeParamsList.sort(Comparator.comparing(RandomRangeParams::getStart));
            }
        }
        // 先检查dataset
        switch (upsertParams.getDataset().toLowerCase()) {
            case "gist":
                datasetEnum = DatasetEnum.GIST;
                fileNames = DatasetUtil.providerFileNames(datasetEnum);
                fileSizeList = DatasetUtil.providerFileSize(fileNames, DatasetEnum.GIST);
                break;
            case "deep":
                datasetEnum = DatasetEnum.DEEP;
                fileNames = DatasetUtil.providerFileNames(datasetEnum);
                fileSizeList = DatasetUtil.providerFileSize(fileNames, DatasetEnum.DEEP);
                break;
            case "sift":
                datasetEnum = DatasetEnum.SIFT;
                fileNames = DatasetUtil.providerFileNames(datasetEnum);
                fileSizeList = DatasetUtil.providerFileSize(fileNames, DatasetEnum.SIFT);
                break;
            case "laion":
                datasetEnum = DatasetEnum.LAION;
                fileNames = DatasetUtil.providerFileNames(datasetEnum);
                fileSizeList = DatasetUtil.providerFileSize(fileNames, DatasetEnum.LAION);
                break;
            case "random":
                break;
            default:
                log.error("传入的数据集名称错误,请检查！");
                return null;
        }
        log.info("文件名称:" + fileNames);
        log.info("文件长度:" + fileSizeList);
        // 要循环upsert的次数--insertRounds
        long upsertRounds = upsertParams.getNumEntries() / upsertParams.getBatchSize();
        float upsertTotalTime = 0;
        log.info("Upsert collection [" + upsertParams.getCollectionName() + "]  from id:" + upsertParams.getStartId() + " , total " + upsertParams.getNumEntries() + " entities... ");
        long startTimeTotal = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(upsertParams.getNumConcurrency());
        ArrayList<Future<UpsertComp.UpsertResultItem>> list = new ArrayList<>();
        // 提前获取collectionSchema，避免每次生成数据时候重复调用describe接口
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());
/*        // 根据startId，剔除之前已经使用过的file
        long tempFileSizeTotal = 0;
        int fileIndex = 0;
        for (int i = 0; i < fileSizeList.size(); i++) {
            tempFileSizeTotal = +fileSizeList.get(0);
            if (tempFileSizeTotal > upsertParams.getStartId()) {
                fileIndex = i;
                break;
            }
        }
        int removeIndex = Math.min(fileIndex+1, fileSizeList.size());
        fileNames.subList(0, removeIndex).clear();
        fileSizeList.subList(0, removeIndex).clear();
        log.info("根据startId，将使用的文件名称:" + fileNames);
        log.info("根据startId，将使用的文件长度:" + fileSizeList);*/

        // 1. 创建RateLimiter实例（根据配置的QPS）
        RateLimiter rateLimiter = null;
        if (upsertParams.getTargetQps() > 0) {
            rateLimiter = RateLimiter.create(upsertParams.getTargetQps());
            log.info("启用QPS控制: {} 请求/秒", upsertParams.getTargetQps());
        }

        // upsert data with multiple threads
        for (int c = 0; c < upsertParams.getNumConcurrency(); c++) {
            RateLimiter finalRateLimiter = rateLimiter;
            int finalC = c;
            List<String> finalFileNames = fileNames;
            List<Long> finalFileSizeList = fileSizeList;
            String finalCollectionName = collectionName;
            Callable callable =
                    () -> {
                        log.info("线程[" + finalC + "]启动...");
                        UpsertResultItem upsertResultItem = new UpsertComp.UpsertResultItem();
                        List<Double> costTime = new ArrayList<>();
                        List<Integer> insertCnt = new ArrayList<>();
                        int retryCount = 0;
                        LocalDateTime endRunningTime = LocalDateTime.now().plusMinutes(upsertParams.getRunningMinutes());
                        for (long r = ((upsertRounds / upsertParams.getNumConcurrency()) * finalC);
                             r < ((upsertRounds / upsertParams.getNumConcurrency()) * (finalC + 1));
                             r++) {
                            // 时间和数据量谁先到都结束
                            if (upsertParams.getRunningMinutes() > 0L && LocalDateTime.now().isAfter(endRunningTime)) {
                                // 3. QPS控制点（如果需要）
                                if (finalRateLimiter != null) {
                                    finalRateLimiter.acquire(); // 阻塞直到获得令牌
                                }
                                log.info("线程[" + finalC + "] Upsert已到设定时长，停止插入...");
                                upsertResultItem.setUpsertCnt(insertCnt);
                                upsertResultItem.setCostTime(costTime);
                                return upsertResultItem;
                            }

                            List<JsonObject> jsonObjects = CommonFunction.genCommonData(finalCollectionName, upsertParams.getBatchSize(),
                                    (r * upsertParams.getBatchSize() + upsertParams.getStartId()), upsertParams.getDataset(), finalFileNames, finalFileSizeList, upsertParams.getGeneralDataRoleList(), upsertParams.getNumEntries(), upsertParams.getStartId(), describeCollectionResp);
                            log.info("线程[" + finalC + "]导入数据 " + upsertParams.getBatchSize() + "条，范围: " + (r * upsertParams.getBatchSize() + upsertParams.getStartId()) + "~" + ((r + 1) * upsertParams.getBatchSize() + upsertParams.getStartId()));
                            UpsertResp upsertResp = null;
                            long startTime = System.currentTimeMillis();
                            try {
                                upsertResp = milvusClientV2.upsert(UpsertReq.builder()
                                        .data(jsonObjects)
                                        .collectionName(finalCollectionName)
                                        .build());
                                if (upsertResp.getUpsertCnt() > 0) {
                                    retryCount = 0;
                                }
                            } catch (Exception e) {
                                log.error("线程[" + finalC + "]" + "upsert error,reason:" + e.getMessage());
                                // 禁写后重试判断
                                if ((!upsertParams.isRetryAfterDeny()) || (retryCount == 10)) {
                                    upsertResultItem.setUpsertCnt(insertCnt);
                                    upsertResultItem.setCostTime(costTime);
                                    upsertResultItem.setExceptionMessage(e.getMessage());
                                    return upsertResultItem;
                                }
                                if (upsertParams.isRetryAfterDeny()) {
                                    retryCount++;
                                    log.info("线程[" + finalC + "]" + "第" + retryCount + "次监测到禁写，等待30秒...");
                                    Thread.sleep(1000 * 30);
                                    continue;
                                }
                            }
                            long endTime = System.currentTimeMillis();
                            costTime.add((endTime - startTime) / 1000.00);
                            insertCnt.add((int) upsertResp.getUpsertCnt());
                            log.info(
                                    "线程 ["
                                            + finalC
                                            + "]Upsert第"
                                            + r
                                            + "批次数据, 成功upsert "
                                            + upsertResp.getUpsertCnt()
                                            + " 条， cost:"
                                            + (endTime - startTime) / 1000.00
                                            + " seconds ");
                        }
                        upsertResultItem.setUpsertCnt(insertCnt);
                        upsertResultItem.setCostTime(costTime);
                        return upsertResultItem;
                    };
            Future<UpsertComp.UpsertResultItem> future = executorService.submit(callable);
            list.add(future);

        }
        long requestNum = 0;
        double costTotal = 0.0;
        CommonResult commonResult;
        UpsertResult upsertResult = null;
        for (Future<UpsertComp.UpsertResultItem> future : list) {
            try {
                UpsertComp.UpsertResultItem upsertResultItem = future.get();
                long count = upsertResultItem.getUpsertCnt().stream().filter(x -> x != 0).count();
                double sum = upsertResultItem.getCostTime().stream().mapToDouble(Double::doubleValue).sum();
                log.info("线程返回结果[UpsertCnt]: " + upsertResultItem.getUpsertCnt());
                log.info("线程返回结果[CostTime]: " + upsertResultItem.getCostTime());
                requestNum += count;
                costTotal += sum;

            } catch (InterruptedException | ExecutionException e) {
                upsertResult = UpsertResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message(e.getMessage()).build())
                        .build();
                return upsertResult;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        upsertTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        // 查询实际导入数据量
        log.info(
                "Total cost of inserting " + requestNum * upsertParams.getBatchSize() + " entities: " + upsertTotalTime + " seconds!");
        log.info("Total insert " + requestNum + " 次数,RPS avg :" + requestNum / upsertTotalTime + " ");
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        upsertResult = UpsertResult.builder()
                .commonResult(commonResult)
                .rps(requestNum / upsertTotalTime)
                .numEntries(requestNum * upsertParams.getBatchSize())
                .requestNum(requestNum)
                .costTime(upsertTotalTime)
                .build();
        executorService.shutdown();
        return upsertResult;
    }

    @Data
    public static class UpsertResultItem {
        private List<Double> costTime;
        private List<Integer> upsertCnt;
        private String exceptionMessage;
    }

    // 删除 index 之前的所有元素（保留 index 及之后的数据）
    public static <T> void removeBeforeIndex(List<T> list, int index) {
        if (index <= 0 || list.isEmpty()) return;

        // 计算实际删除数量（避免越界）
        int removeCount = Math.min(index, list.size());

        // 使用 subList 批量删除（O(1) 时间复杂度）
        list.subList(0, removeCount).clear();
    }
}
