package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DropCollectionResult {
    List<DropCollectionResultItem> dropCollectionResultList;
    List<String> assertMessages;
    /** 目标 collection 总数 */
    Integer totalCount;
    Integer successCount;
    Integer failCount;
    /** true 表示明细因数量过大被截断，只保留部分失败明细 */
    Boolean truncated;
    @Data
    @Builder
    public static class DropCollectionResultItem{
        String collectionName;
        CommonResult commonResult;
    }
}
