package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReleaseResult {
    List<ReleaseResultItem> releaseResultList;
    List<String> assertMessages;
    @Data
    @Builder
    public static class ReleaseResultItem{
        String collectionName;
        CommonResult commonResult;
    }
}
