package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DescribeCollectionResult {
    CommonResult commonResult;
    DescribeCollectionResponse describeCollectionResp;

}
