package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ListIndexesResult {
    CommonResult commonResult;
    List<String> indexNames;
}
