package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ListPartitionsResult {
    List<String> partitionNames;
    CommonResult commonResult;
}
