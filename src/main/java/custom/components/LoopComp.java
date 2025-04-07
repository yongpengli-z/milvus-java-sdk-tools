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
import java.util.Set;

@Slf4j
public class LoopComp {

    public static LoopResult loopComp(LoopParams loopParams) {
        String paramComb = loopParams.getParamComb();

        JSONObject result = new JSONObject();
        int runningNum = loopParams.getCycleNum() == 0 ? 9999 : loopParams.getCycleNum();
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(
                loopParams.getRunningMinutes() == 0 ? 9999 : loopParams.getRunningMinutes());
        int runningCount = 0;
        int exceptionNum=0;
        List<JSONObject> jsonObjBase = new ArrayList<>();
        List<JSONObject> jsonObjBaseList = new ArrayList<>();

        while (runningCount < runningNum && LocalDateTime.now().isBefore(endTime)) {
            log.info("♻️ 第"+runningCount+"次循环 ♻️：");
            List<JSONObject> jsonObjects = ComponentSchedule.runningSchedule(paramComb);
            if (jsonObjBase.size()==0 && !JSONObject.toJSONString(jsonObjects).contains("exception")){
                jsonObjBase = jsonObjects;
                for (int i = 0; i < jsonObjects.size(); i++) {
                    jsonObjBaseList.add(createContainer(jsonObjects.get(i)));
                }
            }
            if (!JSONObject.toJSONString(jsonObjects).contains("exception")){
                if (runningNum <=10){
                    // 将结果全部收集
                    for (int i = 0; i < jsonObjects.size(); i++) {
                        appendValues(jsonObjBaseList.get(i),jsonObjects.get(i));
                    }
                }else{
                    // 将结果里数字类型累加求平均
                    for (int i = 0; i < jsonObjects.size(); i++) {
                        jsonObjBase.set(i,merge(jsonObjBase.get(i),jsonObjects.get(i)));
                    }
                }
            } else {
                exceptionNum ++;
            }

            result.put("Loop_" + runningCount, jsonObjects);
            log.info("♻️ 第"+runningCount+"次运行结果 ♻️："+jsonObjects);
            runningCount++;
        }
        CommonResult commonResult= CommonResult.builder().build();
        if (exceptionNum>0){
            commonResult.setResult(ResultEnum.EXCEPTION.result);
        }else{
            commonResult.setResult(ResultEnum.SUCCESS.result);
        }
        return LoopResult.builder().exceptionNum(exceptionNum)
                .runningNum(runningCount)
                .exceptionNum(exceptionNum)
                .jsonObjectList(runningNum<=10?jsonObjBaseList:jsonObjBase)
                .build();
    }

    private static JSONObject merge(JSONObject json1, JSONObject json2) {
        JSONObject result = new JSONObject();
        Set<String> keys = json1.keySet();

        for (String key : keys) {
            if (!json2.containsKey(key)) continue;

            Object value1 = json1.get(key);
            Object value2 = json2.get(key);

            if (value1 instanceof Number && value2 instanceof Number) {
                // 处理数值类型
                double avg = (((Number) value1).doubleValue()
                        + ((Number) value2).doubleValue()) / 2;
                result.put(key, avg);
            } else if (value1 instanceof JSONObject) {
                // 递归处理嵌套JSON
                result.put(key, merge((JSONObject) value1, (JSONObject) value2));
            } else {
                // 保留原始值（处理String/List等其他类型）
                result.put(key, value1);
            }
        }
        return result;
    }

    /**
     * 创建与输入JSON结构相同的容器对象
     * @param template 结构模板
     * @return 包含List结构的初始化对象
     */
    private static JSONObject createContainer(JSONObject template) {
        JSONObject container = new JSONObject(true); // 保持顺序
        template.keySet().forEach(key -> {
            Object value = template.get(key);
            if (value instanceof JSONObject) {
                // 递归处理嵌套对象
                container.put(key, createContainer((JSONObject) value));
            } else {
                // 初始化空List
                container.put(key, new ArrayList<>());
            }
        });
        return container;
    }

    /**
     * 将JSON值追加到容器对象
     * @param container 目标容器
     * @param json 要合并的JSON
     */
    private static void appendValues(JSONObject container, JSONObject json) {
        container.keySet().forEach(key -> {
            Object containerVal = container.get(key);
            Object jsonVal = json.get(key);

            if (containerVal instanceof JSONObject) {
                // 递归处理嵌套对象
                appendValues((JSONObject) containerVal, (JSONObject) jsonVal);
            } else if (containerVal instanceof List) {
                // 添加值到List
                ((List<Object>) containerVal).add(jsonVal);
            }
        });
    }

    /**
     * 聚合多个JSON的值
     * @param jsons 需要聚合的JSON列表
     * @return 包含所有值的聚合结果
     */
    public static JSONObject aggregate(List<JSONObject> jsons) {
        if (jsons == null || jsons.isEmpty()) return new JSONObject();

        // 创建初始容器
        JSONObject container = createContainer(jsons.get(0));

        // 逐个追加值
        for (JSONObject json : jsons) {
            appendValues(container, json);
        }

        return container;
    }
}
