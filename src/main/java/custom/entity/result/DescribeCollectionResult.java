package custom.entity.result;

import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DescribeCollectionResult {
    CommonResult commonResult;
    DescribeCollectionResp describeCollectionResp;

}
