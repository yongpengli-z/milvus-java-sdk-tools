package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RollingUpgradeResult {
    CommonResult commonResult;
    int costSeconds;
}
