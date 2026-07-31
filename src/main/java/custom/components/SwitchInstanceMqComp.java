package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.entity.SwitchInstanceMqParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SwitchInstanceMqResult;
import custom.utils.CloudOpsServiceUtils;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.envConfig;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class SwitchInstanceMqComp {
    public static SwitchInstanceMqResult switchMq(SwitchInstanceMqParams params) {
        String instanceId = params.getInstanceId() == null || params.getInstanceId().isEmpty()
                ? newInstanceInfo.getInstanceId()
                : params.getInstanceId();
        String regionId = params.getRegionId() == null || params.getRegionId().isEmpty()
                ? envConfig.getRegionId()
                : params.getRegionId();

        CommonResult.CommonResultBuilder commonResultBuilder = CommonResult.builder();
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
                .taskId(data == null ? null : data.getString("taskId"))
                .processInstanceId(data == null ? null : data.getLong("processInstanceId"))
                .build();
    }
}
