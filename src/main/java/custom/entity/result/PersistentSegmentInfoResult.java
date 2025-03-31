package custom.entity.result;

import io.milvus.grpc.PersistentSegmentInfo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PersistentSegmentInfoResult {
    CommonResult commonResult;
    List<PersistentSegmentInfo> persistentSegmentInfoList;
}
