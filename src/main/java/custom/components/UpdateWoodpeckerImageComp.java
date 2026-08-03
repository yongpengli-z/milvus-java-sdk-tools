package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.entity.UpdateWoodpeckerImageParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UpdateWoodpeckerImageResult;
import custom.utils.CloudOpsServiceUtils;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.envConfig;

@Slf4j
public class UpdateWoodpeckerImageComp {
    public static UpdateWoodpeckerImageResult updateImage(UpdateWoodpeckerImageParams params) {
        String regionId = params.getRegionId() == null || params.getRegionId().isEmpty()
                ? envConfig.getRegionId()
                : params.getRegionId();
        params.setNewImageTag(resolveImageTag(params.getNewImageTag()));

        CommonResult.CommonResultBuilder commonResultBuilder = CommonResult.builder();
        if (params.getWoodpeckerId() == null || params.getWoodpeckerId().isEmpty()) {
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message("woodpeckerId is required");
            return result(commonResultBuilder.build(), regionId, params, null);
        }
        if (params.getNewImageTag() == null || params.getNewImageTag().isEmpty()) {
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message("newImageTag is required");
            return result(commonResultBuilder.build(), regionId, params, null);
        }

        String response = CloudOpsServiceUtils.upgradeWoodpeckerImage(regionId, params);
        JSONObject responseJO = JSONObject.parseObject(response);
        Integer code = getCode(responseJO);
        if (code == null || code != 0) {
            commonResultBuilder.result(ResultEnum.EXCEPTION.result).message(getMessage(responseJO));
            return result(commonResultBuilder.build(), regionId, params, null);
        }

        commonResultBuilder.result(ResultEnum.SUCCESS.result);
        return result(commonResultBuilder.build(), regionId, params,
                responseJO.getLong("data"));
    }

    private static String resolveImageTag(String imageTag) {
        if (imageTag == null) {
            return null;
        }
        if (imageTag.contains("(") && imageTag.contains(")")) {
            return imageTag.substring(imageTag.indexOf("(") + 1, imageTag.indexOf(")"));
        }
        return imageTag;
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

    private static UpdateWoodpeckerImageResult result(
            CommonResult commonResult, String regionId, UpdateWoodpeckerImageParams params, Long processInstanceId) {
        return UpdateWoodpeckerImageResult.builder()
                .commonResult(commonResult)
                .regionId(regionId)
                .woodpeckerId(params.getWoodpeckerId())
                .newImageTag(params.getNewImageTag())
                .processInstanceId(processInstanceId)
                .build();
    }
}
