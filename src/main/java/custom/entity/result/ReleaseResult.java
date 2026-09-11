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
    /** 总耗时（秒），批量模式下为整个批量操作的墙钟时间 */
    Float totalCostTime;
    /** 每秒成功操作数 = successCount / totalCostTime */
    Double rps;
    /** 延迟统计（秒），基于每个 collection 的实际 release 耗时 */
    Double avg;
    Double tp99;
    Double tp98;
    Double tp90;
    Double tp85;
    Double tp80;
    Double tp50;
    @Data
    @Builder
    public static class ReleaseResultItem{
        String collectionName;
        /** 该 collection 的 release 耗时（秒）；未执行的占位项为 -1 */
        float costTime;
        CommonResult commonResult;
    }
}
