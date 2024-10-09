package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class LoadResult {
    List<CommonResult> commonResults;
    List<String> collectionNames;
    List<Double> costTimes;
}
