package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.entity.LoopParams;
import custom.entity.result.CommonResult;
import custom.entity.result.LoopResult;
import custom.entity.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class LoopComp {

    public static LoopResult loopComp(LoopParams loopParams, String loopNodeName, List<String> loopParentNodeName) {
        String paramComb = loopParams.getParamComb();

        JSONObject result = new JSONObject();
        int runningNum = loopParams.getCycleNum() == 0 ? 9999 : loopParams.getCycleNum();
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(
                loopParams.getRunningMinutes() == 0 ? 9999 : loopParams.getRunningMinutes());
        int runningCount = 0;
        int exceptionNum = 0;
        List<JSONObject> jsonObjBaseList = new ArrayList<>();

        while (runningCount < runningNum && LocalDateTime.now().isBefore(endTime)) {
            log.info("♻️ 第" + runningCount + "次循环 ♻️：");
            List<JSONObject> jsonObjects = ComponentSchedule.runningSchedule(paramComb);
            if (jsonObjBaseList.size() == 0 && !JSONObject.toJSONString(jsonObjects).contains("exception")) {
                for (int i = 0; i < jsonObjects.size(); i++) {
                    jsonObjBaseList.add(createAggregateStructure(JSON.parseObject(jsonObjects.get(i).toJSONString())));
                }
            }
            if (!JSONObject.toJSONString(jsonObjects).contains("exception")) {
                // 将结果全部收集
                for (int i = 0; i < jsonObjects.size(); i++) {
                    aggregateValues(jsonObjBaseList.get(i), JSON.parseObject(jsonObjects.get(i).toJSONString()));
                }
            } else {
                exceptionNum++;
            }

            result.put("Loop_" + runningCount, jsonObjects);
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
                .resultList(compressAggregateResult(jsonObjBaseList))
                .build();
    }


    /**
     * 创建与输入JSON结构相同的容器对象
     *
     * @param template 结构模板
     * @return 包含List结构的初始化对象
     */
    public static JSONObject createAggregateStructure(JSONObject template) {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof JSONObject) {
                result.put(key, createAggregateStructure((JSONObject) value));
            } else {
                result.put(key, new ArrayList<>());
            }
        }
        return result;
    }

    /**
     * 将JSON值追加到容器对象

     */
    public static void aggregateValues(JSONObject aggregate, JSONObject data) {
        for (Map.Entry<String, Object> entry : aggregate.entrySet()) {
            String key = entry.getKey();
            Object aggValue = entry.getValue();
            Object dataValue = data.get(key);

            if (aggValue instanceof JSONObject) {
                aggregateValues((JSONObject) aggValue, (JSONObject) dataValue);
            } else if (aggValue instanceof List) {
                ((List<Object>) aggValue).add(dataValue);
            }
        }
    }

    /**
     * Loop may run many cycles. Returning every leaf value for every cycle makes
     * the final report very large, so leaf arrays are summarized before return.
     */
    public static List<JSONObject> compressAggregateResult(List<JSONObject> aggregateResult) {
        List<JSONObject> compressedResult = new ArrayList<>();
        for (JSONObject stepResult : aggregateResult) {
            compressedResult.add((JSONObject) compressValue(stepResult));
        }
        return compressedResult;
    }

    private static Object compressValue(Object value) {
        if (value instanceof JSONObject) {
            JSONObject compressed = new JSONObject(true);
            JSONObject jsonObject = (JSONObject) value;
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                compressed.put(entry.getKey(), compressValue(entry.getValue()));
            }
            return compressed;
        }
        if (value instanceof List) {
            return summarizeList((List<?>) value);
        }
        return value;
    }

    private static JSONObject summarizeList(List<?> values) {
        JSONObject summary = new JSONObject(true);
        summary.put("count", values.size());
        if (values.isEmpty()) {
            return summary;
        }

        Set<String> distinctValues = new LinkedHashSet<>();
        boolean allNumber = true;
        boolean allSame = true;
        boolean allScalar = true;
        Object first = values.get(0);
        Object last = values.get(values.size() - 1);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double sum = 0;

        for (Object value : values) {
            if (distinctValues.size() <= 10) {
                distinctValues.add(JSON.toJSONString(value));
            }
            if (!JSON.toJSONString(first).equals(JSON.toJSONString(value))) {
                allSame = false;
            }
            if (value instanceof Number) {
                double numberValue = ((Number) value).doubleValue();
                min = Math.min(min, numberValue);
                max = Math.max(max, numberValue);
                sum += numberValue;
            } else {
                allNumber = false;
            }
            if (!isScalar(value)) {
                allScalar = false;
            }
        }

        if (allSame) {
            summary.put("value", first);
            return summary;
        }

        if (allNumber) {
            summary.put("min", min);
            summary.put("max", max);
            summary.put("avg", sum / values.size());
            summary.put("first", first);
            summary.put("last", last);
            return summary;
        }

        if (allScalar && distinctValues.size() <= 10) {
            List<Object> valuesSample = new ArrayList<>();
            for (String distinctValue : distinctValues) {
                valuesSample.add(JSON.parse(distinctValue));
            }
            summary.put("distinctCount", distinctValues.size());
            summary.put("values", valuesSample);
        } else {
            summary.put("distinctCount", ">10");
            summary.put("first", first);
            summary.put("last", last);
        }
        return summary;
    }

    private static boolean isScalar(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
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
