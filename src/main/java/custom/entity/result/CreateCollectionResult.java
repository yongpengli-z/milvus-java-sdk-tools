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
    /** 总耗时（秒），批量模式下为整个批量操作的墙钟时间 */
    Float totalCostTime;
    /** 每秒成功操作数 = successCount / totalCostTime */
    Double rps;
    /** 延迟统计（秒），基于每个 collection 的实际 create 耗时 */
    Double avg;
    Double tp99;
    Double tp98;
    Double tp90;
    Double tp85;
    Double tp80;
    Double tp50;

    @Data
    @Builder
    public static class CreateCollectionResultItem {
        String collectionName;
        /** 该 collection 的 create 耗时（秒）；未执行的占位项为 -1 */
        float costTime;
        CommonResult commonResult;
    }
}
