package custom.entity.result;

import custom.entity.IndexParams;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CreateIndexResult {
    String collectionName;
    List<IndexParams> indexParams;
    CommonResult commonResult;
    float costTimes;
    List<String> assertMessages;
    /** 多 collection 模式的逐项明细（数量过大时会被截断，只保留部分失败明细） */
    List<CreateIndexResultItem> createIndexResultList;
    Integer totalCount;
    Integer successCount;
    Integer failCount;
    Boolean truncated;
    /** 延迟统计（秒），基于每个 collection 的实际建索引耗时 */
    Double avg;
    Double tp99;
    Double tp98;
    Double tp90;
    Double tp85;
    Double tp80;
    Double tp50;

    @Data
    @Builder
    public static class CreateIndexResultItem {
        String collectionName;
        /** 单个 collection 建索引耗时（秒） */
        float costTime;
        CommonResult commonResult;
    }
}
