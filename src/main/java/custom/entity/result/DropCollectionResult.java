package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DropCollectionResult {
    List<DropCollectionResultItem> dropCollectionResultList;
    @Data
    @Builder
    public static class DropCollectionResultItem{
        String collectionName;
        CommonResult commonResult;
    }
}
