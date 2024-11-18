package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeleteInstanceResult {
    CommonResult commonResult;
    int costSeconds;
}
