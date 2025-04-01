package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QuerySegmentInfoResult {
    CommonResult commonResult;
    List<segmentInfo> segmentInfoList;

    @Data
    @Builder
    public static class segmentInfo{
        long segmentID;
        long collectionID;
        long partitionID;
        long numRows;
        String state;
        String level;
        boolean isSorted;
        String indexName;
        long nodeIds;
    }
}
