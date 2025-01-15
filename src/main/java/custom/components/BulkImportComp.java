package custom.components;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import custom.entity.BulkImportParams;
/*import io.milvus.bulkwriter.BulkImport;
import io.milvus.bulkwriter.request.describe.MilvusDescribeImportRequest;
import io.milvus.bulkwriter.request.import_.MilvusImportRequest;
import io.milvus.bulkwriter.request.list.MilvusListImportJobsRequest;*/
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.importUrl;


@Slf4j
public class BulkImportComp {
    // temp 需要添加根据schema生成新的.npy文件
    /*public static void bulkImport(BulkImportParams bulkImportParams) throws InterruptedException {
        String collectionName = (bulkImportParams.getCollectionName() == null || bulkImportParams.getCollectionName().equals("")) ? globalCollectionNames.get(globalCollectionNames.size()-1) : bulkImportParams.getCollectionName();

        MilvusImportRequest milvusImportRequest = MilvusImportRequest.builder()
                .collectionName(collectionName)
                .partitionName(bulkImportParams.getPartitionName())
                .files(bulkImportParams.getFilePaths())
                .build();
        String bulkImportResult = BulkImport.bulkImport(importUrl, milvusImportRequest);
        JsonObject bulkImportObject = new Gson().fromJson(bulkImportResult, JsonObject.class);
        String jobId = bulkImportObject.getAsJsonObject("data").get("jobId").getAsString();
        log.info("Create a bulkInert task, job id: " + jobId);

        // 巡检导入的结果
        MilvusListImportJobsRequest listImportJobsRequest = MilvusListImportJobsRequest.builder()
                .collectionName(collectionName)
                .build();
        String listImportJobsResult = BulkImport.listImportJobs(importUrl, listImportJobsRequest);
        log.info("listImportJobs: "+listImportJobsResult);
        while (true) {
            System.out.println("Wait 5 second to check bulkInsert job state...");
            TimeUnit.SECONDS.sleep(5);

            log.info("\n===================== getBulkInsertState() ====================");
            MilvusDescribeImportRequest request = MilvusDescribeImportRequest.builder()
                    .jobId(jobId)
                    .build();
            String getImportProgressResult = BulkImport.getImportProgress(importUrl, request);
            log.info("getImportProgress:"+getImportProgressResult);

            JsonObject getImportProgressObject = new Gson().fromJson(getImportProgressResult, JsonObject.class);
            String state = getImportProgressObject.getAsJsonObject("data").get("state").getAsString();
            String progress = getImportProgressObject.getAsJsonObject("data").get("progress").getAsString();
            if ("Failed".equals(state)) {
                String reason = getImportProgressObject.getAsJsonObject("data").get("reason").getAsString();
                log.info(String.format("The job %s failed, reason: %s%n", jobId, reason));
                break;
            } else if ("Completed".equals(state)) {
                log.info(String.format("The job %s completed%n", jobId));
                break;
            } else {
                log.info(String.format("The job %s is running, state:%s progress:%s%n", jobId, state, progress));
            }
        }
    }*/
}
