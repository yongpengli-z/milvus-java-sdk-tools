package custom.components;

import com.google.gson.JsonObject;
import custom.common.CommonFunction;
import custom.entity.InsertParams;
import io.milvus.grpc.GetPersistentSegmentInfoResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SegmentState;
import io.milvus.param.R;
import io.milvus.param.control.GetPersistentSegmentInfoParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class InsertComp {
    public static void insertCollection(InsertParams insertParams) {
        // 要循环insert的次数--insertRounds
        long insertRounds = insertParams.getNumEntries() / insertParams.getBatchSize();
        float insertTotalTime = 0;
        log.info("Insert collection [" + insertParams.getCollectionName() + "] total " + insertParams.getNumEntries() + " entities... ");
        long startTimeTotal = System.currentTimeMillis();
        ExecutorService executorService = Executors.newFixedThreadPool(insertParams.getNumConcurrency());
        ArrayList<Future<List<Integer>>> list = new ArrayList<>();
        // insert data with multiple threads
        String collectionName = (insertParams.getCollectionName() == null ||
                insertParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(0) : insertParams.getCollectionName();

        for (int c = 0; c < insertParams.getNumConcurrency(); c++) {
            int finalC = c;
            Callable callable =
                    () -> {
                        log.info("线程[" + finalC + "]启动...");
                        List<Integer> results = new ArrayList<>();
                        for (long r = (insertRounds / insertParams.getNumConcurrency()) * finalC;
                             r < (insertRounds / insertParams.getNumConcurrency()) * (finalC + 1);
                             r++) {
                            long startTime = System.currentTimeMillis();

                            List<JsonObject> jsonObjects = CommonFunction.genCommonData(collectionName, insertParams.getBatchSize(), r * insertParams.getBatchSize());
                            log.info("线程[" + finalC + "]导入数据 " + insertParams.getBatchSize() + "条，范围: " + r * insertParams.getBatchSize() + "~" + ((r + 1) * insertParams.getBatchSize()));
                            InsertResp insert = null;
                            try {
                                insert = milvusClientV2.insert(InsertReq.builder()
                                        .data(jsonObjects)
                                        .collectionName(collectionName)
                                        .build());
                            } catch (Exception e) {
                                log.error("insert error,reason:"+e.getMessage());
                                return results;
                            }
                            results.add((int) insert.getInsertCnt());
                            long endTime = System.currentTimeMillis();
                            log.info(
                                    "线程 ["
                                            + finalC
                                            + "]插入第"
                                            + r
                                            + "批次数据, 成功导入 "
                                            +  insert.getInsertCnt()
                                            + " 条， cost:"
                                            + (endTime - startTime) / 1000.00
                                            + " seconds ");
                        }

                        return results;
                    };
            Future<List<Integer>> future = executorService.submit(callable);
            list.add(future);

        }
        long requestNum = 0;
        for (Future<List<Integer>> future : list) {
            try {
                long count = future.get().stream().filter(x -> x != 0).count();
                log.info("线程返回结果：" + future.get());
                requestNum += count;
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            long endTimeTotal = System.currentTimeMillis();
            insertTotalTime = (float) ((endTimeTotal - startTimeTotal) / 1000.00);


            log.info(
                    "Total cost of inserting " + insertParams.getNumEntries() + " entities: " + insertTotalTime + " seconds!");
            log.info("Total insert " + requestNum + " 次数,RPS avg :" + requestNum / insertTotalTime);
            executorService.shutdown();
        }
    }

}
