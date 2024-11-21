package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RestartInstanceResult {
    CommonResult commonResult;
    int costSeconds;
}
