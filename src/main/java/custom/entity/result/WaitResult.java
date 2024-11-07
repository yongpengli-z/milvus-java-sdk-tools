package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class WaitResult {
    long waitMinutes;
    CommonResult commonResult;
}
