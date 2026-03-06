package custom.entity.result;

import io.milvus.v2.service.vector.response.QueryResp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GetResult {
    CommonResult commonResult;
    List<QueryResp.QueryResult> getResults;
}
