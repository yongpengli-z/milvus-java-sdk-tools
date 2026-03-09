package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CreateCollectionResult {
    String collectionName;
    CommonResult commonResult;
    List<String> assertMessages;
}
