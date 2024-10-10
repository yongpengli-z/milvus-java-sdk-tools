package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class LoadResult {
    List<LoadResultItem> loadResultList;

    @Builder
    @Data
    public static class LoadResultItem {
        CommonResult commonResult;
        String collectionName;
        Double costTimes;
    }

}
