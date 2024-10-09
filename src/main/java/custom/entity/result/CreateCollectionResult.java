package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateCollectionResult {
    String collectionName;
    CommonResult commonResult;
}
