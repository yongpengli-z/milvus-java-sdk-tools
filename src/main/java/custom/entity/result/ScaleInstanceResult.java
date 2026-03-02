package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScaleInstanceResult {
    CommonResult commonResult;
    int costSeconds;
}
