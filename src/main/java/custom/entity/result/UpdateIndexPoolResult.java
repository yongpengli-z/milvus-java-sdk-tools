package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateIndexPoolResult {
    CommonResult commonResult;
    String currentManagerImage;
    String currentWorkerImage;
}
