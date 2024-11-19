package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class StopInstanceResult {
    CommonResult commonResult;
    int costSeconds;
}
