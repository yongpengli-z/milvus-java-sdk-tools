package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.DeleteInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DeleteInstanceResult;
import custom.entity.result.ResultEnum;
import custom.pojo.InstanceInfo;
import custom.utils.CloudServiceTestUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.*;

@Slf4j
public class DeleteInstanceComp {
    private static final int DEFAULT_WAIT_TIMEOUT_MINUTES = 30;
    private static final int GLOBAL_ROLE_PRIMARY = 1;
    private static final int GLOBAL_ROLE_SECONDARY = 2;

    public static DeleteInstanceResult deleteInstance(DeleteInstanceParams deleteInstanceParams) {
        ensureAccount(deleteInstanceParams);

        String targetInstanceId = resolveTargetInstanceId(deleteInstanceParams);
        if (targetInstanceId == null || targetInstanceId.isEmpty()) {
            return buildResult(ResultEnum.EXCEPTION.result, "instanceId is empty, cannot delete instance", 0);
        }
        if (deleteInstanceParams.isUseOPSTestApi()) {
            String s = CloudServiceTestUtils.deleteInstance(deleteInstanceParams);
            return handleDeleteResponse(s, List.of(targetInstanceId), "Delete instance");
        }

        GlobalDeleteContext globalDeleteContext;
        try {
            globalDeleteContext = resolveGlobalDeleteContext(targetInstanceId);
        } catch (IllegalArgumentException e) {
            return buildResult(ResultEnum.EXCEPTION.result, e.getMessage(), 0);
        }
        if (globalDeleteContext != null) {
            return deleteGlobalClusterFromRm(globalDeleteContext, targetInstanceId);
        }

        String s = ResourceManagerServiceUtils.deleteInstanceById(targetInstanceId);
        return handleDeleteResponse(s, List.of(targetInstanceId), "Delete instance");
    }

    private static void ensureAccount(DeleteInstanceParams params) {
        if (params.getAccountEmail() != null && !params.getAccountEmail().equalsIgnoreCase("")) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(params.getAccountEmail(), params.getAccountPassword());
            return;
        }
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
        }
    }

    private static DeleteInstanceResult deleteGlobalClusterFromRm(GlobalDeleteContext context, String targetInstanceId) {
        long startLoadTime = System.currentTimeMillis();
        log.info("Delete global cluster target from RM: targetId={}, role={}, globalClusterId={}, primaryInstanceId={}, secondaryInstanceIds={}",
                targetInstanceId, context.role, context.globalClusterId, context.primaryInstanceId, context.secondaryInstanceIds);

        if (context.role == GlobalDeleteRole.SECONDARY) {
            String deleteSecondaryResp = ResourceManagerServiceUtils.deleteGlobalSecondaryInstance(
                    targetInstanceId, context.globalClusterId);
            JSONObject deleteSecondaryJO = JSON.parseObject(deleteSecondaryResp);
            if (!isSuccess(deleteSecondaryJO)) {
                return buildResult(ResultEnum.WARNING.result, getMessage(deleteSecondaryJO), elapsedSeconds(startLoadTime));
            }
            if (!waitForInstancesGone(List.of(targetInstanceId))) {
                return buildResult(ResultEnum.WARNING.result,
                        "Delete secondary instance [" + targetInstanceId + "] time out!",
                        elapsedSeconds(startLoadTime));
            }
            return buildResult(ResultEnum.SUCCESS.result, null, elapsedSeconds(startLoadTime));
        }

        if (context.primaryInstanceId == null || context.primaryInstanceId.isEmpty()) {
            return buildResult(ResultEnum.EXCEPTION.result, "primaryInstanceId is empty, cannot disband global cluster", 0);
        }

        for (String secondaryInstanceId : context.secondaryInstanceIds) {
            String deleteSecondaryResp = ResourceManagerServiceUtils.deleteGlobalSecondaryInstance(
                    secondaryInstanceId, context.globalClusterId);
            JSONObject deleteSecondaryJO = JSON.parseObject(deleteSecondaryResp);
            if (!isSuccess(deleteSecondaryJO)) {
                return buildResult(ResultEnum.WARNING.result, getMessage(deleteSecondaryJO), elapsedSeconds(startLoadTime));
            }
            if (!waitForInstancesGone(List.of(secondaryInstanceId))) {
                return buildResult(ResultEnum.WARNING.result,
                        "Delete secondary instance [" + secondaryInstanceId + "] time out!",
                        elapsedSeconds(startLoadTime));
            }
        }

        if (!waitForInstanceStatus(context.primaryInstanceId, InstanceStatusEnum.RUNNING.code)) {
            return buildResult(ResultEnum.WARNING.result,
                    "Wait primary instance [" + context.primaryInstanceId + "] active after deleting secondaries time out!",
                    elapsedSeconds(startLoadTime));
        }

        String disbandResp = ResourceManagerServiceUtils.disbandGlobalCluster(context.globalClusterId, context.primaryInstanceId);
        JSONObject disbandJO = JSON.parseObject(disbandResp);
        if (!isSuccess(disbandJO)) {
            return buildResult(ResultEnum.WARNING.result, getMessage(disbandJO), elapsedSeconds(startLoadTime));
        }

        if (context.role == GlobalDeleteRole.GLOBAL_CLUSTER) {
            return buildResult(ResultEnum.SUCCESS.result, null, elapsedSeconds(startLoadTime));
        }

        String deletePrimaryResp = ResourceManagerServiceUtils.deleteInstanceById(context.primaryInstanceId);
        JSONObject deletePrimaryJO = JSON.parseObject(deletePrimaryResp);
        if (!isSuccess(deletePrimaryJO)) {
            return buildResult(ResultEnum.WARNING.result, getMessage(deletePrimaryJO), elapsedSeconds(startLoadTime));
        }
        if (!waitForInstancesGone(List.of(context.primaryInstanceId))) {
            return buildResult(ResultEnum.WARNING.result,
                    "Delete primary instance [" + context.primaryInstanceId + "] time out!",
                    elapsedSeconds(startLoadTime));
        }

        return buildResult(ResultEnum.SUCCESS.result, null, elapsedSeconds(startLoadTime));
    }

    private static DeleteInstanceResult handleDeleteResponse(String resp, List<String> instanceIds, String actionName) {
        JSONObject jsonObject = JSON.parseObject(resp);
        if (!isSuccess(jsonObject)) {
            return DeleteInstanceResult.builder()
                    .commonResult(CommonResult.builder().message(getMessage(jsonObject))
                            .result(ResultEnum.WARNING.result).build()).build();
        }
        long startLoadTime = System.currentTimeMillis();
        boolean deleted = waitForInstancesGone(instanceIds);
        int costSeconds = elapsedSeconds(startLoadTime);
        log.info("{} cost {} seconds", actionName, costSeconds);
        if (deleted) {
            return DeleteInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result).build())
                    .costSeconds(costSeconds).build();
        }
        return DeleteInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.WARNING.result)
                        .message(actionName + " " + instanceIds + " time out!").build())
                .costSeconds(costSeconds)
                .build();
    }

    private static GlobalDeleteContext resolveGlobalDeleteContext(String targetId) {
        if (isGlobalClusterId(targetId)) {
            GlobalDeleteContext context = loadGlobalDeleteContext(targetId);
            context.role = GlobalDeleteRole.GLOBAL_CLUSTER;
            return context;
        }

        String globalClusterId = resolveKnownGlobalClusterId(targetId);
        if (globalClusterId == null) {
            globalClusterId = ResourceManagerServiceUtils.getGlobalClusterId(targetId);
        }
        if (globalClusterId == null || globalClusterId.isEmpty()) {
            return null;
        }

        GlobalDeleteContext context = loadGlobalDeleteContext(globalClusterId);
        context.role = resolveRole(targetId, context);
        if (context.role == null) {
            context.role = resolveRoleFromDescribe(targetId);
        }
        if (context.role == null) {
            throw new IllegalArgumentException("Cannot determine global cluster member role for instanceId: " + targetId);
        }
        return context;
    }

    private static GlobalDeleteContext loadGlobalDeleteContext(String globalClusterId) {
        GlobalDeleteContext context = new GlobalDeleteContext();
        context.globalClusterId = globalClusterId;
        loadContextFromRuntime(globalClusterId, context);
        loadContextFromCloudServiceTopology(globalClusterId, context);
        return context;
    }

    private static void loadContextFromRuntime(String globalClusterId, GlobalDeleteContext context) {
        String cachedGlobalClusterId = emptyToNull(globalClusterInfo.getInstanceId());
        if (cachedGlobalClusterId != null && !cachedGlobalClusterId.equals(globalClusterId)) {
            return;
        }

        String primaryInstanceId = emptyToNull(primaryInstanceInfo.getInstanceId());
        if (primaryInstanceId == null && isKnownPrimary(newInstanceInfo.getInstanceId())) {
            primaryInstanceId = newInstanceInfo.getInstanceId();
        }
        context.primaryInstanceId = primaryInstanceId;

        for (InstanceInfo secondaryInfo : secondaryInstanceInfoList) {
            addIfNotEmpty(context.secondaryInstanceIds, secondaryInfo.getInstanceId());
        }
    }

    private static void loadContextFromCloudServiceTopology(String globalClusterId, GlobalDeleteContext context) {
        String resp = ResourceManagerServiceUtils.describeGlobalClusterTopology(globalClusterId);
        if (resp == null || resp.isEmpty()) {
            return;
        }
        JSONObject jsonObject = JSON.parseObject(resp);
        if (!isSuccess(jsonObject)) {
            log.warn("describe global cluster topology failed: globalClusterId={}, message={}",
                    globalClusterId, getMessage(jsonObject));
            return;
        }
        JSONObject data = getObjectIgnoreCase(jsonObject, "Data", "data");
        if (data == null) {
            return;
        }

        JSONObject primary = getObjectIgnoreCase(data, "primary", "Primary");
        if (primary != null) {
            String primaryInstanceId = getStringIgnoreCase(primary, "instanceId", "InstanceId");
            if (primaryInstanceId != null && !primaryInstanceId.isEmpty()) {
                context.primaryInstanceId = primaryInstanceId;
            }
        }

        JSONArray secondaries = getArrayIgnoreCase(data, "secondaries", "Secondaries");
        if (secondaries == null) {
            return;
        }
        for (int i = 0; i < secondaries.size(); i++) {
            JSONObject secondary = secondaries.getJSONObject(i);
            String secondaryInstanceId = getStringIgnoreCase(secondary, "instanceId", "InstanceId");
            addIfNotEmpty(context.secondaryInstanceIds, secondaryInstanceId);
        }
    }

    private static GlobalDeleteRole resolveRole(String targetId, GlobalDeleteContext context) {
        if (targetId.equals(context.primaryInstanceId)) {
            return GlobalDeleteRole.PRIMARY;
        }
        for (String secondaryInstanceId : context.secondaryInstanceIds) {
            if (targetId.equals(secondaryInstanceId)) {
                return GlobalDeleteRole.SECONDARY;
            }
        }
        return null;
    }

    private static GlobalDeleteRole resolveRoleFromDescribe(String instanceId) {
        String describeResp = ResourceManagerServiceUtils.describeInstance(instanceId);
        JSONObject describeJO = JSON.parseObject(describeResp);
        if (!isSuccess(describeJO)) {
            return null;
        }
        JSONObject data = getObjectIgnoreCase(describeJO, "Data", "data");
        if (data == null) {
            return null;
        }
        Integer role = getIntegerIgnoreCase(data, "GlobalClusterRole", "globalClusterRole");
        if (role == null) {
            String roleName = getStringIgnoreCase(data, "GlobalClusterRoleName", "globalClusterRoleName", "Role", "role");
            if ("PRIMARY".equalsIgnoreCase(roleName)) {
                return GlobalDeleteRole.PRIMARY;
            }
            if ("SECONDARY".equalsIgnoreCase(roleName)) {
                return GlobalDeleteRole.SECONDARY;
            }
            return null;
        }
        if (role == GLOBAL_ROLE_PRIMARY) {
            return GlobalDeleteRole.PRIMARY;
        }
        if (role == GLOBAL_ROLE_SECONDARY) {
            return GlobalDeleteRole.SECONDARY;
        }
        return null;
    }

    private static String resolveKnownGlobalClusterId(String targetId) {
        String globalClusterId = emptyToNull(globalClusterInfo.getInstanceId());
        if (globalClusterId == null) {
            return null;
        }
        if (targetId.equals(primaryInstanceInfo.getInstanceId()) || targetId.equals(newInstanceInfo.getInstanceId())) {
            return globalClusterId;
        }
        for (InstanceInfo secondaryInfo : secondaryInstanceInfoList) {
            if (targetId.equals(secondaryInfo.getInstanceId())) {
                return globalClusterId;
            }
        }
        return null;
    }

    private static boolean isGlobalClusterId(String id) {
        return id != null && (id.startsWith("glo-") || id.startsWith("gdc-"));
    }

    private static boolean isKnownPrimary(String instanceId) {
        if (instanceId == null) {
            return false;
        }
        String primaryInstanceId = emptyToNull(primaryInstanceInfo.getInstanceId());
        return primaryInstanceId == null || instanceId.equals(primaryInstanceId);
    }

    private static String resolveTargetInstanceId(DeleteInstanceParams params) {
        String instanceId = emptyToNull(params.getInstanceId());
        return instanceId == null ? newInstanceInfo.getInstanceId() : instanceId;
    }

    private static void addIfNotEmpty(List<String> values, String value) {
        if (value == null || value.isEmpty() || values.contains(value)) {
            return;
        }
        values.add(value);
    }

    private static boolean waitForInstancesGone(List<String> instanceIds) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(DEFAULT_WAIT_TIMEOUT_MINUTES);
        while (LocalDateTime.now().isBefore(endTime)) {
            if (!hasAnyInstance(instanceIds)) {
                return true;
            }
            try {
                Thread.sleep(1000 * 10);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !hasAnyInstance(instanceIds);
    }

    private static boolean hasAnyInstance(List<String> instanceIds) {
        List<InstanceInfo> instanceInfoList = CloudServiceUtils.listInstance();
        for (InstanceInfo instanceInfo : instanceInfoList) {
            for (String instanceId : instanceIds) {
                if (instanceId != null && instanceId.equalsIgnoreCase(instanceInfo.getInstanceId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean waitForInstanceStatus(String instanceId, int targetStatus) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(DEFAULT_WAIT_TIMEOUT_MINUTES);
        while (LocalDateTime.now().isBefore(endTime)) {
            String describeResp = ResourceManagerServiceUtils.describeInstance(instanceId);
            JSONObject describeJO = JSON.parseObject(describeResp);
            JSONObject data = getObjectIgnoreCase(describeJO, "Data", "data");
            if (isSuccess(describeJO) && data != null) {
                int status = data.getInteger("Status");
                log.info("Wait instance {} status: {}", instanceId, InstanceStatusEnum.getInstanceStatusByCode(status));
                if (status == targetStatus) {
                    return true;
                }
            }
            try {
                Thread.sleep(1000 * 10);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isSuccess(JSONObject jsonObject) {
        Integer code = jsonObject.getInteger("Code");
        if (code == null) {
            code = jsonObject.getInteger("code");
        }
        return code != null && (code == 0 || code == 200);
    }

    private static String getMessage(JSONObject jsonObject) {
        String message = jsonObject.getString("Message");
        if (message == null) {
            message = jsonObject.getString("message");
        }
        return message;
    }

    private static JSONObject getObjectIgnoreCase(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            JSONObject value = object.getJSONObject(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static JSONArray getArrayIgnoreCase(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            JSONArray value = object.getJSONArray(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String getStringIgnoreCase(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            String value = object.getString(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static Integer getIntegerIgnoreCase(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            Integer value = object.getInteger(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static int elapsedSeconds(long startLoadTime) {
        return (int) ((System.currentTimeMillis() - startLoadTime) / 1000.00);
    }

    private static DeleteInstanceResult buildResult(String result, String message, int costSeconds) {
        return DeleteInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(result)
                        .message(message)
                        .build())
                .costSeconds(costSeconds)
                .build();
    }

    private enum GlobalDeleteRole {
        GLOBAL_CLUSTER,
        PRIMARY,
        SECONDARY
    }

    private static class GlobalDeleteContext {
        private String globalClusterId;
        private String primaryInstanceId;
        private List<String> secondaryInstanceIds = new ArrayList<>();
        private GlobalDeleteRole role;
    }
}
