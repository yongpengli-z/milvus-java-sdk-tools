package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.entity.LoopParams;
import custom.entity.result.CommonResult;
import custom.entity.result.LoopResult;
import custom.entity.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class LoopComp {

    public static LoopResult loopComp(LoopParams loopParams, String loopNodeName, List<String> loopParentNodeName) {
        String paramComb = loopParams.getParamComb();

        int runningNum = loopParams.getCycleNum() == 0 ? 9999 : loopParams.getCycleNum();
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(
                loopParams.getRunningMinutes() == 0 ? 9999 : loopParams.getRunningMinutes());
        int runningCount = 0;
        int exceptionNum = 0;

        while (runningCount < runningNum && LocalDateTime.now().isBefore(endTime)) {
            log.info("♻️ 第" + runningCount + "次循环 ♻️：");
            List<JSONObject> jsonObjects;
            ComponentSchedule.pushLoopContext(loopNodeName, loopParentNodeName);
            try {
                jsonObjects = ComponentSchedule.runningSchedule(paramComb);
            } finally {
                ComponentSchedule.popLoopContext();
            }
            if (JSONObject.toJSONString(jsonObjects).contains("exception")) {
                exceptionNum++;
            }

            log.info("♻️ 第" + runningCount + "次运行结果 ♻️：" + jsonObjects);
            runningCount++;
            reportLoopProgress(loopNodeName, loopParentNodeName, loopParams, runningCount, runningNum, exceptionNum);
        }
        CommonResult commonResult = CommonResult.builder().build();
        if (exceptionNum > 0) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
        } else {
            commonResult.setResult(ResultEnum.SUCCESS.result);
        }
        return LoopResult.builder()
                .result(commonResult)
                .runningNum(runningCount)
                .abnormalNum(exceptionNum)
                .build();
    }

    private static void reportLoopProgress(String loopNodeName, List<String> loopParentNodeName, LoopParams loopParams,
                                           int completedCycles, int maxCycles, int exceptionNum) {
        JSONObject progress = new JSONObject(true);
        progress.put("status", "running");
        progress.put("loopNodeName", loopNodeName);
        progress.put("parentNodeName", loopParentNodeName);
        progress.put("loopPath", buildLoopPath(loopParentNodeName, loopNodeName));
        progress.put("completedCycles", completedCycles);
        progress.put("cycleNum", loopParams.getCycleNum());
        progress.put("maxCycles", maxCycles);
        progress.put("runningMinutes", loopParams.getRunningMinutes());
        progress.put("abnormalNum", exceptionNum);
        ComponentSchedule.reportStepResult(loopNodeName, progress.toJSONString(), loopParentNodeName);
    }

    private static String buildLoopPath(List<String> loopParentNodeName, String loopNodeName) {
        List<String> path = new ArrayList<>(loopParentNodeName);
        path.add(loopNodeName);
        return String.join("/", path);
    }

}
