package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HasPartitionResult {
    boolean hasPartition;
    CommonResult commonResult;
}
