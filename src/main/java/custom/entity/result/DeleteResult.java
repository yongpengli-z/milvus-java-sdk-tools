package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeleteResult {
    CommonResult commonResult;
    Long deletedCount;
}
