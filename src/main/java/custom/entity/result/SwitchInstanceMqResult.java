package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SwitchInstanceMqResult {
    CommonResult commonResult;
    String instanceId;
    String regionId;
    String targetMqType;
    String targetWoodpeckerId;
    String taskId;
    Long processInstanceId;
}
