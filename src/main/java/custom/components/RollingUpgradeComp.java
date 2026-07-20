package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.common.ImageType;
import custom.common.InstanceStatusEnum;
import custom.entity.RollingUpgradeParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.RollingUpgradeResult;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class RollingUpgradeComp {
    public static RollingUpgradeResult rollingUpgradeInstance(RollingUpgradeParams rollingUpgradeParams){
        //查询用户状态，如果未登录，则用临时邮箱账号
        if(cloudServiceUserInfo.getUserId()==null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("") ){
            cloudServiceUserInfo= CloudServiceUtils.queryUserIdOfCloudService(null,null);
        }
        String instanceId = newInstanceInfo.getInstanceId();
        if (instanceId == null || instanceId.equalsIgnoreCase("")) {
            return failResult("instanceId is required for rolling upgrade");
        }
        // 先查询当前实例状态 ----需要修改，不用cloud-service获取，改ops-service获取状态
        String s = ResourceManagerServiceUtils.describeInstance(instanceId);
        log.info("describe instance:"+s);
        JSONObject jsonObject = JSONObject.parseObject(s);
        JSONObject data = getData(jsonObject);
        if (data == null) {
            return failResult("describe instance failed: " + s);
        }
        Integer status = getInteger(data, "Status", "status");
        if (status == null) {
            return failResult("describe instance response missing status: " + s);
        }
        int instanceType = resolveInstanceType(data, instanceId);
        InstanceStatusEnum instanceStatusByCode = InstanceStatusEnum.getInstanceStatusByCode(status);
        log.info("Current status:"+ instanceStatusByCode.toString() + ", instanceType:" + instanceType);
        if (!canUpgrade(instanceType, instanceStatusByCode)){
            return failResult("instance status can't upgrade! Current status:" + instanceStatusByCode);
        }
        String targetDbVersion = resolveTargetDbVersion(rollingUpgradeParams.getTargetDbVersion(), instanceType);
        if (targetDbVersion == null) {
            return failResult("targetDbVersion is required. For VectorLake(in06), pass an exact ins_type=6 dbVersion.");
        }
        rollingUpgradeParams.setTargetDbVersion(targetDbVersion);

        String rollingUpgradeResult;
        if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_MILVUS) {
            rollingUpgradeResult = CloudOpsServiceUtils.rollingUpgrade(instanceId, rollingUpgradeParams);
        } else if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_VECTOR_LAKE) {
            rollingUpgradeResult = ResourceManagerServiceUtils.upgradeVectorLakeCoordinator(instanceId, targetDbVersion);
        } else if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_QUERY_CLUSTER) {
            rollingUpgradeResult = ResourceManagerServiceUtils.upgradeQueryCluster(instanceId, targetDbVersion,
                    rollingUpgradeParams.isForceRestart());
        } else {
            return failResult("unsupported instance type for rolling upgrade: " + instanceType);
        }
        JSONObject jsonObjectResult=JSONObject.parseObject(rollingUpgradeResult);
        Integer responseCode = getInteger(jsonObjectResult, "Code", "code");
        if (responseCode == null || responseCode != 0){
            String message = jsonObjectResult == null ? rollingUpgradeResult : jsonObjectResult.getString("Message");
            if (message == null && jsonObjectResult != null) {
                message = jsonObjectResult.getString("message");
            }
            return RollingUpgradeResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.FAIL.result)
                            .message(message).build()).build();
        }
        long startLoadTime = System.currentTimeMillis();
        try {
            Thread.sleep(1000*20);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
        boolean success;
        if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_MILVUS) {
            success = waitInstanceRunning(instanceId);
        } else if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_VECTOR_LAKE) {
            success = waitVectorLakeRolloutComplete(instanceId);
        } else {
            success = waitQueryClusterRolloutComplete(instanceId);
        }
        long endLoadTime = System.currentTimeMillis();
        log.info("RollingUpgrade cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
        if (success){
            return RollingUpgradeResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build())
                    .costSeconds((int) ((endLoadTime - startLoadTime) / 1000.00)).build();
        }
        return RollingUpgradeResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.FAIL.result)
                        .message("RollingUpgrade time out！").build())
                .build();
    }

    private static boolean waitInstanceRunning(String instanceId) {
        int ruStatus=0;
        LocalDateTime endTime=LocalDateTime.now().plusMinutes(30);
        while(ruStatus!=InstanceStatusEnum.RUNNING.code && LocalDateTime.now().isBefore(endTime)){
            String describeInstance = ResourceManagerServiceUtils.describeInstance(instanceId);
            JSONObject descJO = JSONObject.parseObject(describeInstance);
            JSONObject data = getData(descJO);
            if (data == null || getInteger(data, "Status", "status") == null) {
                log.warn("[RollingUpgrade] describe response missing status:" + describeInstance);
                return false;
            }
            ruStatus = getInteger(data, "Status", "status");
            log.info("[RollingUpgrade] current status:"+ InstanceStatusEnum.getInstanceStatusByCode(ruStatus).toString());
            try {
                if(ruStatus!=InstanceStatusEnum.RUNNING.code) {
                    Thread.sleep(1000 * 10);
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }

        }
        return ruStatus == InstanceStatusEnum.RUNNING.code;
    }

    private static boolean waitVectorLakeRolloutComplete(String instanceId) {
        return waitRolloutComplete(instanceId, true);
    }

    private static boolean waitQueryClusterRolloutComplete(String instanceId) {
        return waitRolloutComplete(instanceId, false);
    }

    private static boolean waitRolloutComplete(String instanceId, boolean vectorLake) {
        LocalDateTime endTime=LocalDateTime.now().plusMinutes(30);
        while (LocalDateTime.now().isBefore(endTime)) {
            String resp = vectorLake
                    ? ResourceManagerServiceUtils.getVectorLakeCoordinatorUpgradeStatus(instanceId)
                    : ResourceManagerServiceUtils.getQueryClusterUpgradeStatus(instanceId);
            JSONObject jo = JSONObject.parseObject(resp);
            JSONObject data = getData(jo);
            if (data != null) {
                Boolean complete = getBoolean(data, "rolloutComplete", "RolloutComplete");
                log.info("[RollingUpgrade] rollout status:" + data.toJSONString());
                if (Boolean.TRUE.equals(complete)) {
                    return true;
                }
            }
            try {
                Thread.sleep(1000 * 10);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }
        return false;
    }

    private static boolean canUpgrade(int instanceType, InstanceStatusEnum status) {
        if (status == null) {
            return false;
        }
        if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_VECTOR_LAKE
                || instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_QUERY_CLUSTER) {
            return status == InstanceStatusEnum.RUNNING
                    || status == InstanceStatusEnum.ABNORMAL
                    || status == InstanceStatusEnum.STOPPED;
        }
        return status == InstanceStatusEnum.RUNNING;
    }

    private static String resolveTargetDbVersion(String input, int instanceType) {
        if (input == null || input.equalsIgnoreCase("")) {
            if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_QUERY_CLUSTER) {
                return "";
            }
            return null;
        }
        if (input.equalsIgnoreCase("latest-release")) {
            if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_QUERY_CLUSTER) {
                return "";
            }
            if (instanceType == ResourceManagerServiceUtils.INSTANCE_TYPE_VECTOR_LAKE) {
                return null;
            }
            List<String> strings = ComponentSchedule.queryReleaseImage();
            return strings.get(0).substring(0, strings.get(0).indexOf("("));
        }
        String latestImageByKeywords = CloudOpsServiceUtils.getLatestImageByKeywords(
                input, ImageType.fromInsType(instanceType).getInsType());
        if (latestImageByKeywords != null && latestImageByKeywords.contains("(")) {
            return latestImageByKeywords.substring(0, latestImageByKeywords.indexOf("("));
        }
        return input;
    }

    private static int resolveInstanceType(JSONObject data, String instanceId) {
        Integer instanceType = getInteger(data, "InstanceType", "instanceType", "InsType", "insType");
        if (instanceType != null) {
            return instanceType;
        }
        String instanceTypeName = getString(data, "InstanceType", "instanceType", "InsType", "insType");
        if ("Milvus".equalsIgnoreCase(instanceTypeName)) {
            return ResourceManagerServiceUtils.INSTANCE_TYPE_MILVUS;
        }
        if ("VectorLake".equalsIgnoreCase(instanceTypeName)) {
            return ResourceManagerServiceUtils.INSTANCE_TYPE_VECTOR_LAKE;
        }
        if ("QueryCluster".equalsIgnoreCase(instanceTypeName)) {
            return ResourceManagerServiceUtils.INSTANCE_TYPE_QUERY_CLUSTER;
        }
        if (instanceId != null) {
            if (instanceId.startsWith("in06-")) {
                return ResourceManagerServiceUtils.INSTANCE_TYPE_VECTOR_LAKE;
            }
            if (instanceId.startsWith("in07-")) {
                return ResourceManagerServiceUtils.INSTANCE_TYPE_QUERY_CLUSTER;
            }
        }
        return ResourceManagerServiceUtils.INSTANCE_TYPE_MILVUS;
    }

    private static JSONObject getData(JSONObject jo) {
        if (jo == null) {
            return null;
        }
        JSONObject data = jo.getJSONObject("Data");
        if (data == null) {
            data = jo.getJSONObject("data");
        }
        return data;
    }

    private static Integer getInteger(JSONObject jo, String... keys) {
        if (jo == null) {
            return null;
        }
        for (String key : keys) {
            Object value = jo.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof String) {
                try {
                    return Integer.valueOf((String) value);
                } catch (NumberFormatException ignored) {
                    // Cloud Ops may return a named instance type such as "Milvus".
                }
            }
        }
        return null;
    }

    private static String getString(JSONObject jo, String... keys) {
        if (jo == null) {
            return null;
        }
        for (String key : keys) {
            Object value = jo.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static Boolean getBoolean(JSONObject jo, String... keys) {
        if (jo == null) {
            return null;
        }
        for (String key : keys) {
            Boolean value = jo.getBoolean(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static RollingUpgradeResult failResult(String message) {
        return RollingUpgradeResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.FAIL.result)
                        .message(message).build()).build();
    }
}
