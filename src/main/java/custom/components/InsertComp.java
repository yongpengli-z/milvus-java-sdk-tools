package custom.components;

import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import custom.common.DatasetEnum;
import custom.entity.InsertParams;
import custom.entity.result.CommonResult;
import custom.entity.result.InsertResult;
import custom.entity.result.ResultEnum;
import custom.utils.DatasetUtil;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class InsertComp {
    public static InsertResult insertCollection(InsertParams insertParams) {
        DatasetEnum datasetEnum;
        List<String> fileNames=new ArrayList<>();
        // 先检查dataset
        switch (insertParams.getDataset().toLowerCase()) {
            case "gist":
                datasetEnum = DatasetEnum.GIST;
                fileNames= DatasetUtil.providerFileNames(datasetEnum);
                log.info("文件名称:"+fileNames);
                break;
            case "deep":
                datasetEnum = DatasetEnum.DEEP;
                fileNames= DatasetUtil.providerFileNames(datasetEnum);
                break;
            case "sift":
                datasetEnum = DatasetEnum.SIFT;
                fileNames= DatasetUtil.providerFileNames(datasetEnum);
                break;
            case "laion":
                datasetEnum = DatasetEnum.LAION;
                fileNames= DatasetUtil.providerFileNames(datasetEnum);
                break;
            case "random":
                break;
            default:
                log.error("传入的数据集名称错误,请检查！");
                return null;
        }
        // 要循环insert的次数--insertRounds
        long insertRounds = insertParams.getNumEntries() / insertParams.getBatchSize();
        float insertTotalTime = 0;
        log.info("Insert collection [" + insertParams.getCollectionName() + "] total " + insertParams.getNumEntries() + " entities... ");
        long startTimeTotal = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(insertParams.getNumConcurrency());
        ArrayList<Future<InsertResultItem>> list = new ArrayList<>();
        // insert data with multiple threads
        String collectionName = (insertParams.getCollectionName() == null ||
                insertParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(0) : insertParams.getCollectionName();

        for (int c = 0; c < insertParams.getNumConcurrency(); c++) {
            int finalC = c;
            List<String> finalFileNames = fileNames;
            Callable callable =
                    () -> {
                        log.info("线程[" + finalC + "]启动...");
                        InsertResultItem insertResultItem = new InsertResultItem();
                        List<Double> costTime = new ArrayList<>();
                        List<Integer> insertCnt = new ArrayList<>();
                        for (long r = (insertRounds / insertParams.getNumConcurrency()) * finalC;
                             r < (insertRounds / insertParams.getNumConcurrency()) * (finalC + 1);
                             r++) {
                            List<JsonObject> jsonObjects = CommonFunction.genCommonData(collectionName, insertParams.getBatchSize(), r * insertParams.getBatchSize(), insertParams.getDataset(), finalFileNames);
                            log.info("线程[" + finalC + "]导入数据 " + insertParams.getBatchSize() + "条，范围: " + r * insertParams.getBatchSize() + "~" + ((r + 1) * insertParams.getBatchSize()));
                            InsertResp insert = null;
                            long startTime = System.currentTimeMillis();
                            try {
                                insert = milvusClientV2.insert(InsertReq.builder()
                                        .data(jsonObjects)
                                        .collectionName(collectionName)
                                        .build());
                            } catch (Exception e) {
                                log.error("insert error,reason:" + e.getMessage());
                                insertResultItem.setInsertCnt(insertCnt);
                                insertResultItem.setCostTime(costTime);
                                return insertResultItem;
                            }
                            long endTime = System.currentTimeMillis();
                            costTime.add((endTime - startTime) / 1000.00);
                            insertCnt.add((int) insert.getInsertCnt());
                            log.info(
                                    "线程 ["
                                            + finalC
                                            + "]插入第"
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
        double costTotal = 0.0;
        CommonResult commonResult;
        InsertResult insertResult = null;
        for (Future<InsertResultItem> future : list) {
            try {
                InsertResultItem insertResultItem = future.get();
                long count = insertResultItem.getInsertCnt().stream().filter(x -> x != 0).count();
                double sum = insertResultItem.getCostTime().stream().mapToDouble(Double::doubleValue).sum();
                log.info("线程返回结果[InsertCnt]: " + insertResultItem.getInsertCnt());
                log.info("线程返回结果[CostTime]: " + insertResultItem.getCostTime());
                requestNum += count;
                costTotal += sum;

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
        log.info("Total insert " + requestNum + " 次数,RPS avg :" + costTotal / requestNum + " ");
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        insertResult = InsertResult.builder()
                .commonResult(commonResult)
                .rps(costTotal / requestNum)
                .numEntries(requestNum * insertParams.getBatchSize())
                .requestNum(requestNum)
                .costTime(insertTotalTime)
                .build();

        executorService.shutdown();
        return insertResult;
    }

    @Data
    public static class InsertResultItem {
        private List<Double> costTime;
        private List<Integer> insertCnt;
    }
}
