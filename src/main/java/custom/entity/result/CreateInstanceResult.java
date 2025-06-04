package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateInstanceResult {
    CommonResult commonResult;
    String uri;
    String instanceId;
    boolean bizCritical;
    boolean monopolized;
}
