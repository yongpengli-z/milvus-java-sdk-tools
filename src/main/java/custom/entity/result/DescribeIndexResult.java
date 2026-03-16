package custom.entity.result;

import io.milvus.v2.service.index.response.DescribeIndexResp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DescribeIndexResult {
    CommonResult commonResult;
    DescribeIndexResp describeIndexResp;
    /**
     * 当 fieldName 和 indexName 都未传时，返回 collection 下所有索引的描述。
     */
    List<DescribeIndexResp> allIndexDescriptions;
}
