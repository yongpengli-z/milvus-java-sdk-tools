package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.entity.RestoreBackupParams;
import custom.entity.result.CommonResult;
import custom.entity.result.RestoreBackupResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudOpsServiceUtils;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class RestoreBackupComp {
    public static RestoreBackupResult restoreBackup(RestoreBackupParams restoreBackupParams) {
        // 先查询backup from instance ID
        String s1 = CloudOpsServiceUtils.queryInstanceIdByBackupId(restoreBackupParams.getBackupId());
        JSONObject jsonObject1 = JSONObject.parseObject(s1);
        Integer code1 = jsonObject1.getInteger("code");
        if (code1 == 0) {
            JSONArray jsonArray = jsonObject1.getJSONObject("data").getJSONArray("list");
            if (jsonArray.size() == 0) {
                return RestoreBackupResult.builder()
                        .commonResult(CommonResult.builder()
                                .message("未找到该备份文件对应的实例Id")
                                .result(ResultEnum.WARNING.result)
                                .build())
                        .build();
            }
            JSONObject jsonObject = jsonArray.getJSONObject(0);
            String fromInstanceId = jsonObject.getString("instanceId");
            restoreBackupParams.setFromInstanceId(fromInstanceId);
        } else {
            return RestoreBackupResult.builder()
                    .commonResult(CommonResult.builder()
                            .message("查询备份文件对应的实例Id失败:" + jsonObject1.getString("message"))
                            .result(ResultEnum.WARNING.result)
                            .build())
                    .build();
        }

        String s = CloudOpsServiceUtils.restoreBackup(restoreBackupParams);
        JSONObject jsonObject = JSON.parseObject(s);
        int code = jsonObject.getInteger("code");
        if (code == 0) {
            String jobId = jsonObject.getString("data");
            // 添加轮询
            int restoreState = -1; // -1 未知， 0 restoring，1 success , 2 deleted , 3 failed
            LocalDateTime endTime = LocalDateTime.now().plusMinutes(60 * 3);
            while (LocalDateTime.now().isBefore(endTime)) {
                String s2 = CloudOpsServiceUtils.queryRestoreBackupStatus(jobId);
                JSONObject jsonObject2 = JSONObject.parseObject(s2);
                if (jsonObject2.getInteger("code") != 0) {
                    CommonResult commonResult = CommonResult.builder().result(ResultEnum.WARNING.result)
                            .message("check restore status:" + jsonObject2.getString("message")).build();
                    return RestoreBackupResult.builder().commonResult(commonResult).build();
                } else {
                    if (jsonObject2.getJSONObject("data").getInteger("total") == 0) {
                        CommonResult commonResult = CommonResult.builder().result(ResultEnum.WARNING.result)
                                .message("check restore result is null，please check jobId！").build();
                        return RestoreBackupResult.builder().commonResult(commonResult).build();
                    }
                    JSONArray jsonArray = jsonObject2.getJSONObject("data").getJSONArray("list");
                    restoreState = jsonArray.getJSONObject(0).getInteger("status");
                    if (restoreState == 1) {
                        CommonResult commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result)
                                .message("Restore success").build();
                        // 重新刷新collectionList
                        globalCollectionNames.clear();
                        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
                        List<String> collectionNames =
                                listCollectionsResp.getCollectionNames();
                        log.info("List collection: " + collectionNames);
                        globalCollectionNames.addAll(collectionNames);
                        return RestoreBackupResult.builder().commonResult(commonResult).build();
                    }
                    if (restoreState == 2) {
                        CommonResult commonResult = CommonResult.builder().result(ResultEnum.WARNING.result)
                                .message("Restore job is deleted").build();
                        return RestoreBackupResult.builder().commonResult(commonResult).build();
                    }
                    if (restoreState == 3) {
                        CommonResult commonResult = CommonResult.builder().result(ResultEnum.WARNING.result)
                                .message("Restore job is failed").build();
                        return RestoreBackupResult.builder().commonResult(commonResult).build();
                    }
                }
                try {
                    Thread.sleep(1000 * 60);
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                }
            }
            // 超时返回
            CommonResult commonResult = CommonResult.builder().result(ResultEnum.WARNING.result)
                    .message("RestoreBackup timeout!").build();
            return RestoreBackupResult.builder().commonResult(commonResult).build();
        } else {
            CommonResult commonResult = CommonResult.builder().result(ResultEnum.WARNING.result)
                    .message("RestoreBackup resp:" + jsonObject.getString("message")).build();
            return RestoreBackupResult.builder().commonResult(commonResult).build();
        }

    }
}
