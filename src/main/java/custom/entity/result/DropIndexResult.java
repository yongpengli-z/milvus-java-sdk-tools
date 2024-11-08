package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DropIndexResult {
    CommonResult commonResult;
    String collectionName;
    String fieldName;
}