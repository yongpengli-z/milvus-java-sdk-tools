package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CreateCollectionResult {
    String collectionName;
    CommonResult commonResult;
    List<String> assertMessages;
    /** 批量创建时的逐项明细（数量过大时会被截断，只保留部分失败明细） */
    List<CreateCollectionResultItem> createCollectionResultList;
    Integer totalCount;
    Integer successCount;
    Integer failCount;
    Boolean truncated;

    @Data
    @Builder
    public static class CreateCollectionResultItem {
        String collectionName;
        CommonResult commonResult;
    }
}
