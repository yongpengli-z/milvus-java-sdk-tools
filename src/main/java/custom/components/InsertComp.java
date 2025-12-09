package custom.components;

import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import custom.common.DatasetEnum;
import custom.entity.InsertParams;
import custom.entity.result.CommonResult;
import custom.entity.result.InsertResult;
import custom.entity.result.ResultEnum;
import custom.utils.DatasetUtil;
import custom.utils.MathUtil;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static custom.BaseTest.*;

@Slf4j
public class InsertComp {
    public static InsertResult insertCollection(InsertParams insertParams) {
        DatasetEnum datasetEnum;
        List<String> fileNames = new ArrayList<>();
        List<Long> fileSizeList = new ArrayList<>();
        Random random = new Random();
        // 先检查dataset
        switch (insertParams.getDataset().toLowerCase()) {
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
        // 要循环insert的次数--insertRounds
        long insertRounds = insertParams.getNumEntries() / insertParams.getBatchSize();
        float insertTotalTime;
        // 判断collection获取规则
        String collectionName = "";
        if (insertParams.getCollectionRule() == null || insertParams.getCollectionRule().equalsIgnoreCase("")) {
            collectionName = (insertParams.getCollectionName() == null ||
                    insertParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : insertParams.getCollectionName();
        } else if (insertParams.getCollectionRule().equalsIgnoreCase("random")) {
            collectionName = globalCollectionNames.get(random.nextInt(globalCollectionNames.size()));
        } else if (insertParams.getCollectionRule().equalsIgnoreCase("sequence")) {
            collectionName = globalCollectionNames.get(insertCollectionIndex);
            insertCollectionIndex += 1;
            insertCollectionIndex = insertCollectionIndex % globalCollectionNames.size();
        } else {
            collectionName = (insertParams.getCollectionName() == null ||
                    insertParams.getCollectionName().equalsIgnoreCase(""))
                    ? globalCollectionNames.get(globalCollectionNames.size() - 1) : insertParams.getCollectionName();
        }
        log.info("Insert collection [" + collectionName + "]  from id:" + insertParams.getStartId() + "total insert " + insertParams.getNumEntries() + " entities... ");
        long startTimeTotal = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(insertParams.getNumConcurrency());
        ArrayList<Future<InsertResultItem>> list = new ArrayList<>();
        // 提前获取collectionSchema，避免每次生成数据时候重复调用describe接口
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());

        // insert data with multiple threads
        for (int c = 0; c < insertParams.getNumConcurrency(); c++) {
            int finalC = c;
            List<String> finalFileNames = fileNames;
            List<Long> finalFileSizeList = fileSizeList;
            String finalCollectionName = collectionName;
            Callable callable =
                    () -> {
                        log.info("线程[" + finalC + "]启动...");
                        InsertResultItem insertResultItem = new InsertResultItem();
                        List<Float> costTime = new ArrayList<>();
                        List<Integer> insertCnt = new ArrayList<>();
                        int retryCount = 0;
                        LocalDateTime endRunningTime = LocalDateTime.now().plusMinutes(insertParams.getRunningMinutes());
                        for (long r = (insertRounds / insertParams.getNumConcurrency()) * finalC;
                             r < (insertRounds / insertParams.getNumConcurrency()) * (finalC + 1);
                             r++) {
                            // 时间和数据量谁先到都结束
                            if (insertParams.getRunningMinutes() > 0L && LocalDateTime.now().isAfter(endRunningTime)) {
                                log.info("线程[" + finalC + "]" + " Insert已到设定时长，停止插入...");
                                insertResultItem.setInsertCnt(insertCnt);
                                insertResultItem.setCostTime(costTime);
                                return insertResultItem;
                            }
                            long genDataStartTime = System.currentTimeMillis();
                            List<JsonObject> jsonObjects = CommonFunction.genCommonData(insertParams.getBatchSize(),
                                    (r * insertParams.getBatchSize() + insertParams.getStartId()), insertParams.getDataset(), finalFileNames, finalFileSizeList, insertParams.getGeneralDataRoleList(), insertParams.getNumEntries(), insertParams.getStartId(), describeCollectionResp);
                            long genDataEndTime = System.currentTimeMillis();
                            log.info("线程[" + finalC + "]insert数据 " + insertParams.getBatchSize() + "条，范围: " + (r * insertParams.getBatchSize() + insertParams.getStartId()) + "~" + ((r + 1) * insertParams.getBatchSize() + insertParams.getStartId()));
//                            log.info("线程[" + finalC + "]insert数据 " + insertParams.getBatchSize() + "条，生成数据耗时: " + (genDataEndTime - genDataStartTime) / 1000.00 + " seconds");
                            InsertResp insert = null;
                            long startTime = System.currentTimeMillis();
                            long endTime = 0;
                            try {
                                insert = milvusClientV2.insert(InsertReq.builder()
                                        .data(jsonObjects)
                                        .collectionName(finalCollectionName)
                                        .build());
                                endTime = System.currentTimeMillis();
                                costTime.add((float) ((endTime - startTime) / 1000.00));
                                if (insert.getInsertCnt() > 0) {
                                    retryCount = 0;
                                }
                            } catch (Exception e) {
                                if (insertParams.isIgnoreError()) {
                                    log.error("线程[" + finalC + "]" + "Ignore error，continue insert......");
                                    continue;
                                }
                                log.error("线程[" + finalC + "]" + "insert error,reason:" + e.getMessage());
                                // 禁写后重试判断
                                if ((!insertParams.isRetryAfterDeny()) || (retryCount == 10)) {
                                    insertResultItem.setInsertCnt(insertCnt);
                                    insertResultItem.setCostTime(costTime);
                                    insertResultItem.setExceptionMessage(e.getMessage());
                                    return insertResultItem;
                                }
                                if (insertParams.isRetryAfterDeny()) {
                                    retryCount++;
                                    log.info("线程[" + finalC + "]" + "第" + retryCount + "次监测到禁写，等待30秒...");
                                    Thread.sleep(1000 * 30);
                                    continue;
                                }
                            }

                            insertCnt.add((int) insert.getInsertCnt());
                            log.info(
                                    "线程 ["
                                            + finalC
                                            + "]insert第"
                                            + r
                                            + "批次数据, 成功导入 "
                                            + insert.getInsertCnt()
                                            + " 条， cost:"
                                            + (endTime - startTime) / 1000.00
                                            + " seconds ");
                        }
                        insertResultItem.setInsertCnt(insertCnt);
                        insertResultItem.setCostTime(costTime);
                        return insertResultItem;
                    };
            Future<InsertResultItem> future = executorService.submit(callable);
            list.add(future);

        }

        long requestNum = 0;
        List<Float> costTimeTotal = new ArrayList<>();
        CommonResult commonResult;
        InsertResult insertResult;
        String exceptionFinally = "";
        for (Future<InsertResultItem> future : list) {
            try {
                InsertResultItem insertResultItem = future.get();
                long count = insertResultItem.getInsertCnt().stream().filter(x -> x != 0).count();
//                double sum = insertResultItem.getCostTime().stream().mapToDouble(Float::floatValue).sum();
                exceptionFinally = insertResultItem.getExceptionMessage() != null ? insertResultItem.getExceptionMessage() : exceptionFinally;
                log.info("线程返回结果[InsertCnt]: " + insertResultItem.getInsertCnt());
                log.info("线程返回结果[CostTime]: " + insertResultItem.getCostTime());
                requestNum += count;
                costTimeTotal.addAll(insertResultItem.getCostTime());

            } catch (InterruptedException | ExecutionException e) {
                insertResult = InsertResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.EXCEPTION.result)
                                .message(e.getMessage()).build())
                        .build();
                return insertResult;
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        insertTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        // 查询实际导入数据量
        log.info(
                "Total cost of inserting " + requestNum * insertParams.getBatchSize() + " entities: " + insertTotalTime + " seconds!");
        log.info("Total insert " + requestNum + " 次数,RPS avg :" + requestNum / insertTotalTime + " ");
        log.info("Avg:" + MathUtil.calculateAverage(costTimeTotal));
        log.info("TP99:" + MathUtil.calculateTP99(costTimeTotal, 0.99f));
        log.info("TP98:" + MathUtil.calculateTP99(costTimeTotal, 0.98f));
        log.info("TP90:" + MathUtil.calculateTP99(costTimeTotal, 0.90f));
        log.info("TP85:" + MathUtil.calculateTP99(costTimeTotal, 0.85f));
        log.info("TP80:" + MathUtil.calculateTP99(costTimeTotal, 0.80f));
        log.info("TP50:" + MathUtil.calculateTP99(costTimeTotal, 0.50f));
        if (exceptionFinally.equalsIgnoreCase("")) {
            commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        } else {
            commonResult = CommonResult.builder().result(ResultEnum.EXCEPTION.result)
                    .message(exceptionFinally).build();
        }
        insertResult = InsertResult.builder()
                .commonResult(commonResult)
                .rps(requestNum / insertTotalTime)
                .numEntries(requestNum * insertParams.getBatchSize())
                .requestNum(requestNum)
                .costTime(insertTotalTime)
                .avg(MathUtil.calculateAverage(costTimeTotal))
                .tp99(MathUtil.calculateTP99(costTimeTotal, 0.99f))
                .tp98(MathUtil.calculateTP99(costTimeTotal, 0.98f))
                .tp90(MathUtil.calculateTP99(costTimeTotal, 0.90f))
                .tp85(MathUtil.calculateTP99(costTimeTotal, 0.85f))
                .tp80(MathUtil.calculateTP99(costTimeTotal, 0.80f))
                .tp50(MathUtil.calculateTP99(costTimeTotal, 0.50f))
                .build();
        executorService.shutdown();
        return insertResult;
    }

    @Data
    public static class InsertResultItem {
        private List<Float> costTime;
        private List<Integer> insertCnt;
        private String exceptionMessage;
    }
}
