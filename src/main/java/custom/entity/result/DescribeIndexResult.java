package custom.entity.result;

import io.milvus.v2.service.index.response.DescribeIndexResp;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DescribeIndexResult {
    CommonResult commonResult;
    DescribeIndexResp describeIndexResp;
}
