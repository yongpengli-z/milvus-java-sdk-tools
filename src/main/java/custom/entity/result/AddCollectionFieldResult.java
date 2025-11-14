package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddCollectionFieldResult {
    CommonResult commonResult;
    String collectionName;
}
