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
import java.util.List;
import java.util.Map;

@Slf4j
public class LoopComp {

    public static LoopResult loopComp(LoopParams loopParams) {
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
        }
        CommonResult commonResult = CommonResult.builder().build();
        if (exceptionNum > 0) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
        } else {
            commonResult.setResult(ResultEnum.SUCCESS.result);
        }
        return LoopResult.builder().exceptionNum(exceptionNum)
                .runningNum(runningCount)
                .exceptionNum(exceptionNum)
                .resultList(jsonObjBaseList)
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

}
