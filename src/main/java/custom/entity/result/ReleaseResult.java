package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReleaseResult {
    List<ReleaseResultItem> releaseResultList;
    List<String> assertMessages;
    /** 目标 collection 总数 */
    Integer totalCount;
    /** release 成功数 */
    Integer successCount;
    /** release 失败数 */
    Integer failCount;
    /** true 表示 releaseResultList 因数量过大被截断，只保留部分失败明细 */
    Boolean truncated;
    @Data
    @Builder
    public static class ReleaseResultItem{
        String collectionName;
        CommonResult commonResult;
    }
}
