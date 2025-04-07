package custom.entity.result;


import com.alibaba.fastjson.JSONObject;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoopResult {
    CommonResult result;
    List<JSONObject> jsonObjectList;
    int runningNum;
    int exceptionNum;

}
