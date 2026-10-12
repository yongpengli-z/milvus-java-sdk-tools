package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.common.InstanceStatusEnum;
import custom.entity.DeleteInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DeleteInstanceResult;
import custom.entity.result.ResultEnum;
import custom.exception.CustomException;
import custom.exception.CustomExceptionCode;
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
    private static final String QUERY_CLUSTER_ID_PREFIX = "in07-";

    /**
     * 删除 QueryCluster(in07)：有 session token 走 cloud-service，无 token 走 OpenAPI(apiKey)。
     * QC 删完后级联清理 VL(in06) 共享底座——但仅当 (project, region) 下已无任何 QC，
     * 避免删到其他任务正在复用的底座。
     */
    private static DeleteInstanceResult deleteQueryCluster(String clusterId) {
        long startTime = System.currentTimeMillis();
        String projectId = cloudServiceUserInfo.getDefaultProjectId();
        String regionId = envConfig.getRegionId();
        boolean noToken = cloudServiceUserInfo.getToken() == null || cloudServiceUserInfo.getToken().isEmpty();
        boolean openApi = noToken && CloudServiceUtils.openApiChannelAvailable();
        if (noToken && !openApi) {
            return buildResult(ResultEnum.EXCEPTION.result,
                    "delete query cluster 需要 session token 或注入 apiKey，当前账号两者都没有", 0);
        }
        log.info("[DeleteQueryCluster] clusterId={}, channel={}", clusterId, openApi ? "open-api(apiKey)" : "cloud-service(token)");
        String resp = openApi
                ? CloudServiceUtils.deleteQueryClusterByOpenApi(clusterId)
                : CloudServiceUtils.deleteQueryCluster(projectId, regionId, clusterId);
        JSONObject respJO;
        try {
            respJO = JSON.parseObject(resp);
        } catch (Exception e) {
            return buildResult(ResultEnum.EXCEPTION.result, "delete query cluster resp parse failed: " + resp, 0);
        }
        if (!isSuccess(respJO)) {
            return buildResult(ResultEnum.EXCEPTION.result, "delete query cluster failed: " + getMessage(respJO), 0);
        }
        boolean gone = waitQueryClusterGone(clusterId, openApi, projectId, regionId);
        if (gone) {
            cascadeDeleteIdleVectorLake(openApi, projectId, regionId);
        }
        int costSeconds = elapsedSeconds(startTime);
        if (gone) {
            return buildResult(ResultEnum.SUCCESS.result, null, costSeconds);
        }
        return buildResult(ResultEnum.WARNING.result,
                "Delete query cluster [" + clusterId + "] time out!", costSeconds);
    }

    /**
     * QC 删完后，若 (project, region) 下已无 QC，则删除 VL 底座（UAT 资源回收）。
     * fail-open：VL 删除失败不影响 QC 删除结果。
     */
    private static void cascadeDeleteIdleVectorLake(boolean openApi, String projectId, String regionId) {
        try {
            if (queryClusterExists(openApi, projectId, regionId)) {
                log.info("[DeleteQueryCluster] 还有其它 QueryCluster 复用 VL，保留底座");
                return;
            }
            String vectorLakeId = ResourceManagerServiceUtils.findVectorLakeInstanceId();
            if (vectorLakeId == null || vectorLakeId.isEmpty()) {
                log.info("[DeleteQueryCluster] 未找到 VL 实例，无需级联删除");
                return;
            }
            log.info("[DeleteQueryCluster] 级联删除空闲 VL: {}", vectorLakeId);
            String delResp = ResourceManagerServiceUtils.deleteVectorLakeById(vectorLakeId);
            JSONObject delJO = JSON.parseObject(delResp);
            if (!isSuccess(delJO)) {
                log.warn("[DeleteQueryCluster] 级联删除 VL 失败: {}", getMessage(delJO));
                return;
            }
            // VL 删除是异步 workflow（delete_vectorlake_ins），轮询等收敛
            LocalDateTime endTime = LocalDateTime.now().plusMinutes(10);
            while (LocalDateTime.now().isBefore(endTime)) {
                String describeResp = ResourceManagerServiceUtils.describeInstance(vectorLakeId);
                JSONObject data = JSON.parseObject(describeResp).getJSONObject("Data");
                if (data == null) {
                    break;
                }
                Integer status = data.getInteger("Status");
                if (status != null && status == InstanceStatusEnum.DELETED.code) {
                    break;
                }
                Thread.sleep(15000);
            }
            ComponentSchedule.updateInstanceStatus(vectorLakeId, "", "", InstanceStatusEnum.DELETED.code);
        } catch (Exception e) {
            log.warn("[DeleteQueryCluster] 级联删除 VL 异常（不影响 QC 删除结果）: {}", e.getMessage());
        }
    }

    private static boolean queryClusterExists(boolean openApi, String projectId, String regionId) {
        String resp = openApi
                ? CloudServiceUtils.listQueryClustersByOpenApi(projectId, regionId)
                : CloudServiceUtils.listQueryClusters(projectId, regionId);
        JSONObject jo = JSON.parseObject(resp);
        Object dataRaw = jo.get("Data") != null ? jo.get("Data") : jo.get("data");
        JSONArray clusters = null;
        if (dataRaw instanceof JSONArray) {
            clusters = (JSONArray) dataRaw;
        } else if (dataRaw instanceof JSONObject) {
            clusters = ((JSONObject) dataRaw).getJSONArray("onDemandClusters");
            if (clusters == null) {
                clusters = ((JSONObject) dataRaw).getJSONArray("OnDemandClusters");
            }
        }
        return clusters != null && !clusters.isEmpty();
    }

    private static boolean waitQueryClusterGone(String clusterId, boolean openApi, String projectId, String regionId) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(10);
        while (LocalDateTime.now().isBefore(endTime)) {
            try {
                String resp = openApi
                        ? CloudServiceUtils.getQueryClusterByOpenApi(clusterId)
                        : CloudServiceUtils.getQueryCluster(projectId, regionId, clusterId);
                JSONObject jo = JSON.parseObject(resp);
                Integer code = jo.getInteger("Code") != null ? jo.getInteger("Code") : jo.getInteger("code");
                if (code == null || code != 0) {
                    // 99201(No VectorLake)/99202(Query cluster not found)/message 含 not found 都视为已删
                    String message = jo.getString("Message") != null ? jo.getString("Message") : jo.getString("message");
                    if ((code != null && (code == 99201 || code == 99202))
                            || (message != null && message.toLowerCase().contains("not found"))) {
                        return true;
                    }
                } else {
                    JSONObject data = jo.getJSONObject("Data") != null ? jo.getJSONObject("Data") : jo.getJSONObject("data");
                    String status = data == null ? null
                            : (data.getString("status") != null ? data.getString("status") : data.getString("Status"));
                    if (status != null && status.equalsIgnoreCase("DELETED")) {
                        return true;
                    }
                }
            } catch (Exception e) {
                log.warn("[DeleteQueryCluster] wait gone check error: {}", e.getMessage());
            }
            try {
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }


    public static DeleteInstanceResult deleteInstance(DeleteInstanceParams deleteInstanceParams) {
        ensureAccount(deleteInstanceParams);

        String targetInstanceId = resolveTargetInstanceId(deleteInstanceParams);
        if (targetInstanceId == null || targetInstanceId.isEmpty()) {
            return buildResult(ResultEnum.EXCEPTION.result, "instanceId is empty, cannot delete instance", 0);
        }

        // in07 QueryCluster 必须走专属删除通道：activity-provider 的通用 delete 分发没有
        // ins_type=7（NPE 假成功），且 in07 不可能是 global cluster 成员，提前短路。
        if (targetInstanceId.startsWith(QUERY_CLUSTER_ID_PREFIX)) {
            return deleteQueryCluster(targetInstanceId);
        }

        GlobalDeleteContext globalDeleteContext;
        try {
            globalDeleteContext = resolveGlobalDeleteContext(targetInstanceId);
        } catch (CustomException e) {
            return buildResult(ResultEnum.EXCEPTION.result, e.getMessage(), 0);
        }
        if (globalDeleteContext != null) {
            return deleteGlobalCluster(globalDeleteContext, targetInstanceId, deleteInstanceParams.isUseCloudTestApi());
        }

        if (deleteInstanceParams.isUseCloudTestApi()) {
            String s = CloudServiceTestUtils.deleteInstanceById(targetInstanceId);
            return handleDeleteResponse(s, List.of(targetInstanceId), "Delete instance");
        }

        String s = ResourceManagerServiceUtils.deleteInstanceById(targetInstanceId);
        return handleDeleteResponse(s, List.of(targetInstanceId), "Delete instance");
    }

    private static void ensureAccount(DeleteInstanceParams params) {
        // 只用全局 cloudServiceUserInfo（QTP 注入的提交人账号），为空才回退默认账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().isEmpty()) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
        }
    }

    private static DeleteInstanceResult deleteGlobalCluster(GlobalDeleteContext context, String targetInstanceId, boolean useCloudTestApi) {
        long startLoadTime = System.currentTimeMillis();
        String deleteApi = useCloudTestApi ? "cloud-test" : "rm-service";
        log.info("Delete global cluster target from {}: targetId={}, role={}, globalClusterId={}, primaryInstanceId={}, secondaryInstanceIds={}",
                deleteApi, targetInstanceId, context.role, context.globalClusterId, context.primaryInstanceId, context.secondaryInstanceIds);

        if (context.role == GlobalDeleteRole.SECONDARY) {
            String deleteSecondaryResp = deleteSecondaryInstance(targetInstanceId, context.globalClusterId, useCloudTestApi);
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
            String deleteSecondaryResp = deleteSecondaryInstance(secondaryInstanceId, context.globalClusterId, useCloudTestApi);
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

        String deletePrimaryResp = deleteInstanceById(context.primaryInstanceId, useCloudTestApi);
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

    private static String deleteSecondaryInstance(String instanceId, String globalClusterId, boolean useCloudTestApi) {
        if (useCloudTestApi) {
            return CloudServiceTestUtils.deleteInstanceById(instanceId);
        }
        return ResourceManagerServiceUtils.deleteGlobalSecondaryInstance(instanceId, globalClusterId);
    }

    private static String deleteInstanceById(String instanceId, boolean useCloudTestApi) {
        if (useCloudTestApi) {
            return CloudServiceTestUtils.deleteInstanceById(instanceId);
        }
        return ResourceManagerServiceUtils.deleteInstanceById(instanceId);
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
            throw new CustomException(CustomExceptionCode.INVALID_RESPONSE,
                    "Cannot determine global cluster member role for instanceId: " + targetId);
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
        if (code == null || (code != 0 && code != 200)) {
            return false;
        }
        JSONObject innerResponse = getInnerResponse(jsonObject);
        if (innerResponse == null) {
            return true;
        }
        Integer innerCode = innerResponse.getInteger("Code");
        if (innerCode == null) {
            innerCode = innerResponse.getInteger("code");
        }
        return innerCode != null && (innerCode == 0 || innerCode == 200);
    }

    private static String getMessage(JSONObject jsonObject) {
        JSONObject innerResponse = getInnerResponse(jsonObject);
        if (innerResponse != null) {
            String innerMessage = getMessage(innerResponse);
            if (innerMessage != null && !innerMessage.isEmpty()) {
                return innerMessage;
            }
        }
        String message = jsonObject.getString("Message");
        if (message == null) {
            message = jsonObject.getString("message");
        }
        return message;
    }

    private static JSONObject getInnerResponse(JSONObject jsonObject) {
        String data = jsonObject.getString("Data");
        if (data == null) {
            data = jsonObject.getString("data");
        }
        if (data == null || !data.trim().startsWith("{")) {
            return null;
        }
        try {
            JSONObject innerResponse = JSON.parseObject(data);
            if (innerResponse.containsKey("Code") || innerResponse.containsKey("code")) {
                return innerResponse;
            }
        } catch (Exception e) {
            log.warn("Ignore non-json response data: {}", data);
        }
        return null;
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
