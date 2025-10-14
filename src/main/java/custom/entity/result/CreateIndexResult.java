package custom.entity.result;

import custom.entity.IndexParams;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CreateIndexResult {
    String collectionName;
    List<IndexParams> indexParams;
    CommonResult commonResult;
    float costTimes;
}
