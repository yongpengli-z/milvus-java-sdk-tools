package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.entity.LoopParams;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
public class LoopComp {

    public static JSONObject loopComp(LoopParams loopParams) {
        String paramComb = loopParams.getParamComb();

        JSONObject result = new JSONObject();
        int runningNum = loopParams.getCycleNum() == 0 ? 9999 : loopParams.getCycleNum();
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(
                loopParams.getRunningMinutes() == 0 ? 9999 : loopParams.getRunningMinutes());
        int runningCount = 1;
        while (runningCount <= runningNum && LocalDateTime.now().isBefore(endTime)) {
            List<JSONObject> jsonObjects = ComponentSchedule.runningSchedule(paramComb);
            result.put("Loop_" + runningCount, jsonObjects);
            runningCount++;
        }
        return result;
    }
}
