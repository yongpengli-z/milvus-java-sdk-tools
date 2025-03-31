package custom.entity.result;

import io.milvus.grpc.QuerySegmentInfo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuerySegmentInfoResult {
    CommonResult commonResult;
    List<QuerySegmentInfo> querySegmentInfoList;

}
