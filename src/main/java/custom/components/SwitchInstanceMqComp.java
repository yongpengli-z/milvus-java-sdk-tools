package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.entity.SwitchInstanceMqParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SwitchInstanceMqResult;
import custom.utils.CloudOpsServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

import static custom.BaseTest.envConfig;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class SwitchInstanceMqComp {
    private static final String PULSAR = "pulsar";
    private static final int CLEANUP_WAIT_TIMEOUT_MINUTES = 30;
    private static final long CLEANUP_POLL_INTERVAL_MS = 30_000L;

    public static SwitchInstanceMqResult switchMq(SwitchInstanceMqParams params) {
        String instanceId = params.getInstanceId() == null || params.getInstanceId().isEmpty()
                ? newInstanceInfo.getInstanceId()
                : params.getInstanceId();
        String regionId = params.getRegionId() == null || params.getRegionId().isEmpty()
                ? envConfig.getRegionId()
                : params.getRegionId();

        CommonResult.CommonResultBuilder commonResultBuilder = CommonResult.builder();
        CleanupContext cleanupContext = new CleanupContext();
        if (PULSAR.equalsIgnoreCase(params.getTargetMqType())) {
            CleanupResult cleanupResult = cleanupHistoricalTopicsIfNeeded(instanceId, regionId, params);
            if (!cleanupResult.success) {
                commonResultBuilder.result(ResultEnum.EXCEPTION.result).message(cleanupResult.message);
                return SwitchInstanceMqResult.builder()
                        .commonResult(commonResultBuilder.build())
                        .instanceId(instanceId)
                        .regionId(regionId)
                        .targetMqType(params.getTargetMqType())
                        .targetWoodpeckerId(params.getTargetWoodpeckerId())
                        .cleanupTaskId(cleanupResult.cleanupTaskId)
                        .cleanupProcessInstanceId(cleanupResult.cleanupProcessInstanceId)
                        .build();
            }
            cleanupContext.cleanupTaskId = cleanupResult.cleanupTaskId;
            cleanupContext.cleanupProcessInstanceId = cleanupResult.cleanupProcessInstanceId;
        }

        String response = CloudOpsServiceUtils.switchInstanceMq(instanceId, regionId, params);
        JSONObject responseJO = JSONObject.parseObject(response);
        Integer code = responseJO.getInteger("code");
        if (code == null) {
            code = responseJO.getInteger("Code");
        }
        if (code == null || code != 0) {
            String message = responseJO.getString("message");
            if (message == null || message.isEmpty()) {
                message = responseJO.getString("Message");
            }
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message(message);
            return SwitchInstanceMqResult.builder()
                    .commonResult(commonResultBuilder.build())
                    .instanceId(instanceId)
                    .regionId(regionId)
                    .targetMqType(params.getTargetMqType())
                    .targetWoodpeckerId(params.getTargetWoodpeckerId())
                    .cleanupTaskId(cleanupContext.cleanupTaskId)
                    .cleanupProcessInstanceId(cleanupContext.cleanupProcessInstanceId)
                    .build();
        }

        JSONObject data = responseJO.getJSONObject("data");
        commonResultBuilder.result(ResultEnum.SUCCESS.result);
        return SwitchInstanceMqResult.builder()
                .commonResult(commonResultBuilder.build())
                .instanceId(instanceId)
                .regionId(regionId)
                .targetMqType(params.getTargetMqType())
                .targetWoodpeckerId(params.getTargetWoodpeckerId())
                .cleanupTaskId(cleanupContext.cleanupTaskId)
                .cleanupProcessInstanceId(cleanupContext.cleanupProcessInstanceId)
                .taskId(data == null ? null : data.getString("taskId"))
                .processInstanceId(data == null ? null : data.getLong("processInstanceId"))
                .build();
    }

    private static CleanupResult cleanupHistoricalTopicsIfNeeded(
            String instanceId, String regionId, SwitchInstanceMqParams params) {
        PreviewResult preview = preview(instanceId, regionId, params);
        if (!preview.success) {
            return CleanupResult.fail(preview.message);
        }
        if (preview.canSubmit) {
            return CleanupResult.success(null, null);
        }
        CleanupContext cleanupContext = new CleanupContext();
        if (preview.cleanupAvailable && preview.cleanupTransferTaskId != null
                && !preview.cleanupTransferTaskId.isEmpty()) {
            CleanupSubmitResult submitResult = submitCleanup(instanceId, regionId, preview.cleanupTransferTaskId);
            if (!submitResult.success) {
                return CleanupResult.fail(submitResult.message);
            }
            cleanupContext.cleanupTaskId = submitResult.cleanupTaskId;
            cleanupContext.cleanupProcessInstanceId = submitResult.cleanupProcessInstanceId;
        } else if (preview.cleanupProcessInstanceId == null || preview.cleanupProcessInstanceId.isEmpty()) {
            return CleanupResult.fail(preview.blocker == null || preview.blocker.isEmpty()
                    ? "Switch to pulsar is blocked before cleanup"
                    : preview.blocker);
        }

        LocalDateTime endTime = LocalDateTime.now().plusMinutes(CLEANUP_WAIT_TIMEOUT_MINUTES);
        while (LocalDateTime.now().isBefore(endTime)) {
            try {
                Thread.sleep(CLEANUP_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CleanupResult.fail("Interrupted while waiting for historical topic cleanup");
            }
            preview = preview(instanceId, regionId, params);
            if (!preview.success) {
                return CleanupResult.fail(preview.message);
            }
            if (preview.canSubmit) {
                return CleanupResult.success(
                        cleanupContext.cleanupTaskId, cleanupContext.cleanupProcessInstanceId);
            }
            if (preview.cleanupAvailable) {
                return CleanupResult.fail("Historical topic cleanup did not finish successfully: "
                        + preview.blocker);
            }
            log.info("waiting historical MQ topic cleanup before switch pulsar, instanceId={}, blocker={}",
                    instanceId, preview.blocker);
        }
        return CleanupResult.fail("Wait historical topic cleanup timeout after "
                + CLEANUP_WAIT_TIMEOUT_MINUTES + " minutes");
    }

    private static PreviewResult preview(String instanceId, String regionId, SwitchInstanceMqParams params) {
        String response = CloudOpsServiceUtils.previewSwitchInstanceMq(instanceId, regionId, params);
        JSONObject responseJO = JSONObject.parseObject(response);
        Integer code = getCode(responseJO);
        if (code == null || code != 0) {
            return PreviewResult.fail(getMessage(responseJO));
        }
        JSONObject data = responseJO.getJSONObject("data");
        if (data == null) {
            return PreviewResult.fail("Preview switch MQ response missing data");
        }
        PreviewResult result = new PreviewResult();
        result.success = true;
        result.canSubmit = Boolean.TRUE.equals(data.getBoolean("canSubmit"));
        result.cleanupAvailable = Boolean.TRUE.equals(data.getBoolean("cleanupAvailable"));
        result.cleanupTransferTaskId = data.getString("cleanupTransferTaskId");
        result.cleanupProcessInstanceId = data.getString("cleanupProcessInstanceId");
        result.blocker = data.getString("blocker");
        return result;
    }

    private static CleanupSubmitResult submitCleanup(String instanceId, String regionId, String transferTaskId) {
        String response = CloudOpsServiceUtils.cleanupInstanceMqTopics(instanceId, regionId, transferTaskId);
        JSONObject responseJO = JSONObject.parseObject(response);
        Integer code = getCode(responseJO);
        if (code == null || code != 0) {
            return CleanupSubmitResult.fail(getMessage(responseJO));
        }
        JSONObject data = responseJO.getJSONObject("data");
        return CleanupSubmitResult.success(
                data == null ? null : data.getString("taskId"),
                data == null ? null : data.getLong("processInstanceId"));
    }

    private static Integer getCode(JSONObject responseJO) {
        if (responseJO == null) {
            return null;
        }
        Integer code = responseJO.getInteger("code");
        return code == null ? responseJO.getInteger("Code") : code;
    }

    private static String getMessage(JSONObject responseJO) {
        if (responseJO == null) {
            return "Empty Cloud Ops response";
        }
        String message = responseJO.getString("message");
        if (message == null || message.isEmpty()) {
            message = responseJO.getString("Message");
        }
        return message == null || message.isEmpty() ? responseJO.toJSONString() : message;
    }

    private static class CleanupContext {
        private String cleanupTaskId;
        private Long cleanupProcessInstanceId;
    }

    private static class PreviewResult {
        private boolean success;
        private boolean canSubmit;
        private boolean cleanupAvailable;
        private String cleanupTransferTaskId;
        private String cleanupProcessInstanceId;
        private String blocker;
        private String message;

        private static PreviewResult fail(String message) {
            PreviewResult result = new PreviewResult();
            result.success = false;
            result.message = message;
            return result;
        }
    }

    private static class CleanupSubmitResult {
        private boolean success;
        private String cleanupTaskId;
        private Long cleanupProcessInstanceId;
        private String message;

        private static CleanupSubmitResult success(String cleanupTaskId, Long cleanupProcessInstanceId) {
            CleanupSubmitResult result = new CleanupSubmitResult();
            result.success = true;
            result.cleanupTaskId = cleanupTaskId;
            result.cleanupProcessInstanceId = cleanupProcessInstanceId;
            return result;
        }

        private static CleanupSubmitResult fail(String message) {
            CleanupSubmitResult result = new CleanupSubmitResult();
            result.success = false;
            result.message = message;
            return result;
        }
    }

    private static class CleanupResult {
        private boolean success;
        private String cleanupTaskId;
        private Long cleanupProcessInstanceId;
        private String message;

        private static CleanupResult success(String cleanupTaskId, Long cleanupProcessInstanceId) {
            CleanupResult result = new CleanupResult();
            result.success = true;
            result.cleanupTaskId = cleanupTaskId;
            result.cleanupProcessInstanceId = cleanupProcessInstanceId;
            return result;
        }

        private static CleanupResult fail(String message) {
            CleanupResult result = new CleanupResult();
            result.success = false;
            result.message = message;
            return result;
        }
    }
}
