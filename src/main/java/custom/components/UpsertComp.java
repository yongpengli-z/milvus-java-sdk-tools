package custom.components;

import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import custom.common.DatasetEnum;
import custom.entity.UpsertParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UpsertResult;
import custom.utils.DatasetUtil;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class UpsertComp {
    public static UpsertResult upsertCollection(UpsertParams upsertParams){
        // 先search collection
        String collectionName = (upsertParams.getCollectionName() == null ||
                upsertParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(0) : upsertParams.getCollectionName();

        DatasetEnum datasetEnum;
        List<String> fileNames = new ArrayList<>();
        List<Long> fileSizeList = new ArrayList<>();
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
        log.info("Upsert collection [" + upsertParams.getCollectionName() + "]  from id:"+upsertParams.getStartId()+" , total " + upsertParams.getNumEntries() + " entities... ");
        long startTimeTotal = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(upsertParams.getNumConcurrency());
        ArrayList<Future<UpsertComp.UpsertResultItem>> list = new ArrayList<>();

       // upsert data with multiple threads
        for (int c = 0; c < upsertParams.getNumConcurrency(); c++) {
            int finalC = c;
            List<String> finalFileNames = fileNames;
            List<Long> finalFileSizeList = fileSizeList;
            Callable callable =
                    () -> {
                        log.info("线程[" + finalC + "]启动...");
                        UpsertResultItem upsertResultItem = new UpsertComp.UpsertResultItem();
                        List<Double> costTime = new ArrayList<>();
                        List<Integer> insertCnt = new ArrayList<>();
                        for (long r = ((upsertRounds / upsertParams.getNumConcurrency()) * finalC)+upsertParams.getStartId();
                             r < ((upsertRounds / upsertParams.getNumConcurrency()) * (finalC + 1))+upsertParams.getStartId();
                             r++) {
                            List<JsonObject> jsonObjects = CommonFunction.genCommonData(collectionName, upsertParams.getBatchSize(),
                                    r * upsertParams.getBatchSize()+upsertParams.getStartId(), upsertParams.getDataset(), finalFileNames, finalFileSizeList);
                            log.info("线程[" + finalC + "]导入数据 " + upsertParams.getBatchSize() + "条，范围: " + r * upsertParams.getBatchSize()+upsertParams.getStartId() + "~" + ((r + 1) * upsertParams.getBatchSize()+upsertParams.getStartId()));
                            UpsertResp upsertResp = null;
                            long startTime = System.currentTimeMillis();
                            try {
                                upsertResp = milvusClientV2.upsert(UpsertReq.builder()
                                        .data(jsonObjects)
                                        .collectionName(collectionName)
                                        .build());
                            } catch (Exception e) {
                                log.error("upsert error,reason:" + e.getMessage());
                                upsertResultItem.setUpsertCnt(insertCnt);
                                upsertResultItem.setCostTime(costTime);
                                return upsertResultItem;
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
        log.info("Total insert " + requestNum + " 次数,RPS avg :" + costTotal / requestNum + " ");
        commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        upsertResult = UpsertResult.builder()
                .commonResult(commonResult)
                .rps(costTotal / requestNum)
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
    }
}
