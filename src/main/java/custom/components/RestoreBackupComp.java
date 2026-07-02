package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.common.BackupDatasetEnum;
import custom.entity.RestoreBackupParams;
import custom.entity.result.CommonResult;
import custom.entity.result.RestoreBackupResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudOpsServiceUtils;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static custom.BaseTest.envEnum;
import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class RestoreBackupComp {
    public static RestoreBackupResult restoreBackup(RestoreBackupParams restoreBackupParams) {
        RestoreBackupResult resolveResult = resolveBackupId(restoreBackupParams);
        if (resolveResult != null) {
            return resolveResult;
        }

        RestoreBackupResult fromInstanceResult = resolveFromInstanceId(restoreBackupParams);
        if (fromInstanceResult != null) {
            return fromInstanceResult;
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

    private static RestoreBackupResult resolveBackupId(RestoreBackupParams restoreBackupParams) {
        if (restoreBackupParams.getBackupId() != null && !restoreBackupParams.getBackupId().isEmpty()) {
            return null;
        }

        BackupDatasetEnum backupDatasetEnum = null;
        String backupPreset = restoreBackupParams.getBackupPreset();
        if (backupPreset != null && !backupPreset.isEmpty()) {
            backupDatasetEnum = BackupDatasetEnum.findBySelectName(envEnum, backupPreset);
            if (backupDatasetEnum == null) {
                backupDatasetEnum = BackupDatasetEnum.fromName(backupPreset);
            }
            if (backupDatasetEnum == null) {
                return warningResult("未找到预置备份: env=" + (envEnum == null ? "" : envEnum.region)
                        + ", backupPreset=" + backupPreset);
            }
        } else if (hasDatasetBackupSelection(restoreBackupParams)) {
            if (envEnum == null) {
                return warningResult("当前环境为空，无法按数据集自动选择 backupId");
            }
            if (!isCompleteDatasetBackupSelection(restoreBackupParams)) {
                return warningResult("请完整填写 backupDataset、backupDim、backupRowCount");
            }
            backupDatasetEnum = BackupDatasetEnum.find(
                    envEnum,
                    restoreBackupParams.getBackupDataset(),
                    restoreBackupParams.getBackupDim(),
                    restoreBackupParams.getBackupRowCount());
            if (backupDatasetEnum == null) {
                return warningResult("未找到当前环境的预置备份: env=" + envEnum.region
                        + ", dataset=" + restoreBackupParams.getBackupDataset()
                        + ", dim=" + restoreBackupParams.getBackupDim()
                        + ", rowCount=" + restoreBackupParams.getBackupRowCount());
            }
        }

        if (backupDatasetEnum != null) {
            restoreBackupParams.setBackupId(backupDatasetEnum.backupId);
            if (backupDatasetEnum.fromInstanceId != null && !backupDatasetEnum.fromInstanceId.isEmpty()) {
                restoreBackupParams.setFromInstanceId(backupDatasetEnum.fromInstanceId);
            }
            log.info("使用预置备份: preset={}, selectName={}, env={}, dataset={}, dim={}, rowCount={}, backupId={}, fromInstanceId={}",
                    backupDatasetEnum.presetName,
                    backupDatasetEnum.selectName,
                    backupDatasetEnum.env.region,
                    backupDatasetEnum.datasetName,
                    backupDatasetEnum.dim,
                    backupDatasetEnum.rowCount,
                    backupDatasetEnum.backupId,
                    backupDatasetEnum.fromInstanceId);
        }

        if (restoreBackupParams.getBackupId() == null || restoreBackupParams.getBackupId().isEmpty()) {
            return warningResult("backupId 为空，请填写 backupId、backupPreset 或 backupDataset/backupDim/backupRowCount");
        }
        return null;
    }

    private static RestoreBackupResult resolveFromInstanceId(RestoreBackupParams restoreBackupParams) {
        if (restoreBackupParams.getFromInstanceId() != null && !restoreBackupParams.getFromInstanceId().isEmpty()) {
            return null;
        }

        // 兜底兼容手填 backupId，或枚举里暂未记录 fromInstanceId 的备份。
        String s1 = CloudOpsServiceUtils.queryInstanceIdByBackupId(restoreBackupParams.getBackupId());
        JSONObject jsonObject1 = JSONObject.parseObject(s1);
        Integer code1 = jsonObject1.getInteger("code");
        if (code1 == 0) {
            JSONArray jsonArray = jsonObject1.getJSONObject("data").getJSONArray("list");
            if (jsonArray.size() == 0) {
                return warningResult("未找到该备份文件对应的实例Id");
            }
            JSONObject jsonObject = jsonArray.getJSONObject(0);
            String fromInstanceId = jsonObject.getString("instanceId");
            restoreBackupParams.setFromInstanceId(fromInstanceId);
            return null;
        }
        return warningResult("查询备份文件对应的实例Id失败:" + jsonObject1.getString("message"));
    }

    private static boolean hasDatasetBackupSelection(RestoreBackupParams restoreBackupParams) {
        return (restoreBackupParams.getBackupDataset() != null && !restoreBackupParams.getBackupDataset().isEmpty())
                || restoreBackupParams.getBackupDim() > 0
                || restoreBackupParams.getBackupRowCount() > 0;
    }

    private static boolean isCompleteDatasetBackupSelection(RestoreBackupParams restoreBackupParams) {
        return restoreBackupParams.getBackupDataset() != null
                && !restoreBackupParams.getBackupDataset().isEmpty()
                && restoreBackupParams.getBackupDim() > 0
                && restoreBackupParams.getBackupRowCount() > 0;
    }

    private static RestoreBackupResult warningResult(String message) {
        CommonResult commonResult = CommonResult.builder()
                .result(ResultEnum.WARNING.result)
                .message(message)
                .build();
        return RestoreBackupResult.builder().commonResult(commonResult).build();
    }
}
