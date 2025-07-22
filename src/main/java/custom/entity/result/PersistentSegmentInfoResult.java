package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PersistentSegmentInfoResult {
    CommonResult commonResult;
    SegmentInfoList segmentInfoList;
    int segmentCount;
    @Data
    @Builder
    public static class SegmentInfoList{
        long collectionId;
        List<Long> segmentIDList;
        List<Long> partitionIDList;
        List<Long> numRowsList;
        List<String> stateList;
        List<String> levelList;
        List<Boolean> isSortedList;
    }
}
