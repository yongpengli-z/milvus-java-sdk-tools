package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CompactResult {
    List<CompactResult.CompactResultItem> compactResultList;

    @Builder
    @Data
    public static class CompactResultItem {
        CommonResult commonResult;
        String collectionName;
        Double costTimes;
    }
}
