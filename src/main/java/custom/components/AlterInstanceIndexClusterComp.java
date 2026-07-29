package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.AlterInstanceIndexClusterParams;
import custom.entity.result.AlterInstanceIndexClusterResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class AlterInstanceIndexClusterComp {
    public static AlterInstanceIndexClusterResult alterIndexCluster(AlterInstanceIndexClusterParams alterInstanceIndexClusterParams){
        // 检查账号（如果指定了 accountEmail 则强制用该账号登录）
        if (alterInstanceIndexClusterParams.getAccountEmail() != null && !alterInstanceIndexClusterParams.getAccountEmail().equalsIgnoreCase("")) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(alterInstanceIndexClusterParams.getAccountEmail(), alterInstanceIndexClusterParams.getAccountPassword());
        } else if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
        }
        String s = CloudOpsServiceUtils.alterIndexCluster(alterInstanceIndexClusterParams);
        JSONObject jsonObject = JSONObject.parseObject(s);
        Integer code = jsonObject.getInteger("code");
        CommonResult.CommonResultBuilder commonResultBuilder = CommonResult.builder();
        if (code==0){
            String instanceId = alterInstanceIndexClusterParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : alterInstanceIndexClusterParams.getInstanceId();

            // 内嵌重启
            if (alterInstanceIndexClusterParams.isNeedRestart()) {
                log.info("[AlterIndexCluster] alter success, start ops restart instance: " + instanceId);
                String restartResp = CloudOpsServiceUtils.restartInstance(instanceId);
                JSONObject restartJO;
                try {
                    restartJO = JSONObject.parseObject(restartResp);
                } catch (Exception e) {
                    commonResultBuilder.result(ResultEnum.WARNING.result)
                            .message("AlterIndexCluster success but ops restart returned invalid response: " + restartResp);
                    return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                            .instanceId(instanceId)
                            .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                            .build();
                }
                if (restartJO == null) {
                    commonResultBuilder.result(ResultEnum.WARNING.result)
                            .message("AlterIndexCluster success but ops restart returned empty response");
                    return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                            .instanceId(instanceId)
                            .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                            .build();
                }
                Integer restartCode = restartJO.getInteger("code");
                if (restartCode == null) {
                    restartCode = restartJO.getInteger("Code");
                }
                if (restartCode == null || restartCode != 0) {
                    String msg = restartJO.getString("message");
                    if (msg == null || msg.equalsIgnoreCase("")) {
                        msg = restartJO.getString("Message");
                    }
                    log.warn("[AlterIndexCluster] ops restart failed: " + msg);
                    commonResultBuilder.result(ResultEnum.WARNING.result)
                            .message("AlterIndexCluster success but ops restart failed: " + msg);
                    return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                            .instanceId(instanceId)
                            .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                            .build();
                }
                int costSeconds = waitInstanceRunning(instanceId);
                if (costSeconds < 0) {
                    commonResultBuilder.result(ResultEnum.WARNING.result)
                            .message("AlterIndexCluster success but ops restart timed out");
                    return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                            .instanceId(instanceId)
                            .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                            .build();
                }
                log.info("[AlterIndexCluster] ops restart success, cost " + costSeconds + "s");
            }

            commonResultBuilder.result(ResultEnum.SUCCESS.result);
            return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build())
                    .instanceId(instanceId)
                    .currentIndexClusterId(alterInstanceIndexClusterParams.getIndexClusterId())
                    .build();
        }else {
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message(jsonObject.getString("message"));
            return AlterInstanceIndexClusterResult.builder().commonResult(commonResultBuilder.build()).build();
        }
    }

    private static int waitInstanceRunning(String instanceId) {
        int status;
        long startTime = System.currentTimeMillis();
        try {
            Thread.sleep(1000 * 10L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage());
            return -1;
        }
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(30);
        do {
            String descResult = ResourceManagerServiceUtils.describeInstance(instanceId);
            JSONObject descJO = JSONObject.parseObject(descResult);
            JSONObject data = descJO.getJSONObject("Data");
            if (data == null || data.getInteger("Status") == null) {
                log.warn("[AlterIndexCluster] describe instance response missing status: " + descResult);
                return -1;
            }
            status = data.getInteger("Status");
            InstanceStatusEnum instanceStatus = InstanceStatusEnum.getInstanceStatusByCode(status);
            log.info("[AlterIndexCluster] current instance status:" + instanceStatus);
            try {
                if (status != InstanceStatusEnum.RUNNING.code) {
                    Thread.sleep(1000 * 10L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error(e.getMessage());
                return -1;
            }
        } while (status != InstanceStatusEnum.RUNNING.code && LocalDateTime.now().isBefore(endTime));
        if (status == InstanceStatusEnum.RUNNING.code) {
            return (int) ((System.currentTimeMillis() - startTime) / 1000.00);
        }
        return -1;
    }
}
