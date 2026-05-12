package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PauseResult {
    String reason;
    String qtpResponse;
    int resumeStatus;
    CommonResult commonResult;
}
