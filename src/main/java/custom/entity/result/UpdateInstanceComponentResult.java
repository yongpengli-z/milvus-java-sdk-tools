package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * UpdateInstanceComponent 返回结果。
 * <p>
 * costSeconds 含 restart 等待时间；changes 记录本次实际发起的 RM update 调用明细，
 * 任一失败时 commonResult 为 WARNING 且 changes 的末尾即为失败项。
 */
@Data
@Builder
public class UpdateInstanceComponentResult {

    CommonResult commonResult;

    /** 本次执行的 update 动作明细，按调用顺序记录。 */
    List<ChangeRecord> changes;

    /** restart 后等到 RUNNING 的总耗时（秒），含所有 update + restart 轮询时间。 */
    int costSeconds;

    @Data
    @Builder
    public static class ChangeRecord {
        /** NodeCategory specName，例 queryNode。 */
        String category;
        /** 副本组下标，null 表示作用于所有副本组。 */
        Integer replicaIndex;
        /** 动作类型：replicas / requests / limits。 */
        String action;
        /** 本次 payload 简述（JSON 片段）。 */
        String payload;
        /** RM 是否成功。 */
        boolean success;
        /** 失败时填 RM 返回的 message，或异常消息。 */
        String message;
    }
}
