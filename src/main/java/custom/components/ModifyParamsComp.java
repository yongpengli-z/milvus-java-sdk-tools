package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.InstanceStatusEnum;
import custom.entity.ModifyParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ModifyParamsResult;
import custom.entity.result.ResultEnum;
import custom.pojo.ParamInfo;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.cloudServiceUserInfo;

@Slf4j
public class ModifyParamsComp {
    public static ModifyParamsResult modifyParams(ModifyParams modifyParams) {
        // 如果指定了 accountEmail，强制切换到该账号，避免沿用 BaseTest 初始化的默认账号。
        if (modifyParams.getAccountEmail() != null && !modifyParams.getAccountEmail().equalsIgnoreCase("")) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(
                    modifyParams.getAccountEmail(), modifyParams.getAccountPassword());
        } else if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
        }
        // 筛选出修改的参数还是需要新增的参数
        List<ParamInfo> paramInfoList
                = ResourceManagerServiceUtils.listParams(modifyParams.getInstanceId());
        List<ModifyParams.Params> paramsList = modifyParams.getParamsList();
        List<ModifyParams.Params> needModifyParams = new ArrayList<>();
        List<ModifyParams.Params> needAddParams = new ArrayList<>();
        for (ModifyParams.Params param : paramsList) {
            ParamInfo paramInfo = paramInfoList.stream().filter(x -> x.getParamName().equalsIgnoreCase(param.getParamName())).findFirst().orElse(null);
            if (paramInfo == null) {
                needAddParams.add(param);
            }
            if (paramInfo != null) {
                needModifyParams.add(param);
            }
        }

        // 修改参数
        List<String> rmErrors = new ArrayList<>();
        if (needModifyParams.size() > 0) {
            rmErrors.addAll(ResourceManagerServiceUtils.modifyParams(modifyParams.getInstanceId(), needModifyParams));
        }
        if (needAddParams.size() > 0) {
            rmErrors.addAll(ResourceManagerServiceUtils.addParams(modifyParams.getInstanceId(), needAddParams));
        }
        if (!rmErrors.isEmpty()) {
            return ModifyParamsResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.FAIL.result)
                            .message(String.join("; ", rmErrors)).build()).build();
        }

        if (modifyParams.isNeedRestart()) {
            String s = ResourceManagerServiceUtils.restartInstance(modifyParams.getInstanceId());
            JSONObject sJO = JSONObject.parseObject(s);
            if (sJO.getInteger("Code") == null || sJO.getInteger("Code") != 0) {
                return ModifyParamsResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.FAIL.result)
                                .message(sJO.getString("Message")).build()).build();
            }
            // 轮询结果
            int ruStatus;
            long startLoadTime = System.currentTimeMillis();
            try {
                Thread.sleep(1000 * 20);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
            LocalDateTime endTime = LocalDateTime.now().plusMinutes(30);
            do {
                String descResult = ResourceManagerServiceUtils.describeInstance(modifyParams.getInstanceId());
                log.debug("describe instance:"+descResult);
                JSONObject descJO = JSONObject.parseObject(descResult);
                ruStatus = descJO.getJSONObject("Data").getInteger("Status");
                InstanceStatusEnum instanceStatusE = InstanceStatusEnum.getInstanceStatusByCode(ruStatus);
                log.info("Current instance status:" + instanceStatusE.toString());
                try {
                    if (ruStatus != InstanceStatusEnum.RUNNING.code) {
                        Thread.sleep(1000 * 10);
                    }
                } catch (InterruptedException e) {
                    log.error(e.getMessage());
                }
            } while (ruStatus != InstanceStatusEnum.RUNNING.code && LocalDateTime.now().isBefore(endTime));
            long endLoadTime = System.currentTimeMillis();
            log.info("Restart instance cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");

        }

        // 查询当前参数
        List<ModifyParamsResult.Params> currentParams = new ArrayList<>();
        List<ParamInfo> currentParamList
                = ResourceManagerServiceUtils.listParams(modifyParams.getInstanceId());
        List<String> unmatchedParams = new ArrayList<>();
        List<String> pendingParams = new ArrayList<>();
        for (ModifyParams.Params params : paramsList) {
            ParamInfo paramInfo = currentParamList.stream().filter(x -> x.getParamName().equalsIgnoreCase(params.getParamName())).findFirst().orElse(null);
            if (paramInfo == null) {
                unmatchedParams.add(params.getParamName() + " not found after modify");
                currentParams.add(ModifyParamsResult.Params.builder().paramName(params.getParamName())
                        .expectedValue(params.getParamValue()).build());
                continue;
            }
            currentParams.add(ModifyParamsResult.Params.builder().paramName(params.getParamName())
                    .expectedValue(params.getParamValue())
                    .currentValue(paramInfo.getCurrentValue())
                    .finalValue(paramInfo.getFinalValue()).build());

            boolean currentMatched = valuesEqual(paramInfo.getCurrentValue(), params.getParamValue());
            boolean finalMatched = valuesEqual(paramInfo.getFinalValue(), params.getParamValue());
            if (!currentMatched && !finalMatched) {
                unmatchedParams.add(params.getParamName() + " expected=" + params.getParamValue()
                        + ", current=" + paramInfo.getCurrentValue() + ", final=" + paramInfo.getFinalValue());
            } else if (!currentMatched) {
                pendingParams.add(params.getParamName() + " finalValue=" + paramInfo.getFinalValue()
                        + ", currentValue=" + paramInfo.getCurrentValue());
            }

        }

        CommonResult.CommonResultBuilder commonResult = CommonResult.builder();
        if (!unmatchedParams.isEmpty()) {
            commonResult.result(ResultEnum.FAIL.result)
                    .message("Modify result mismatch: " + String.join("; ", unmatchedParams));
        } else if (!pendingParams.isEmpty()) {
            commonResult.result(ResultEnum.WARNING.result)
                    .message("Param value was written to finalValue but currentValue is not effective yet: "
                            + String.join("; ", pendingParams));
        } else {
            commonResult.result(ResultEnum.SUCCESS.result);
        }

        CommonResult finalCommonResult = commonResult.build();
        return ModifyParamsResult.builder().commonResult(finalCommonResult)
                .paramsList(currentParams).build();


    }

    private static boolean valuesEqual(String actual, String expected) {
        return actual != null && expected != null && actual.trim().equals(expected.trim());
    }
}
