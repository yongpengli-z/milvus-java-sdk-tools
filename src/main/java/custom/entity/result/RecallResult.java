package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Recall 评测结果。
 */
@Data
@Builder
public class RecallResult {

    /**
     * 通用结果（成功/失败/异常）。
     */
    CommonResult commonResult;

    /**
     * 实际使用的 query 向量数（nq）。
     */
    int nq;

    /**
     * 从 collection 采样的向量数。
     */
    int sampleNum;

    /**
     * Ground truth 使用的 search level。
     */
    int groundTruthLevel;

    /**
     * 总耗时（秒），包含 ground truth 计算和所有组合评测。
     */
    double totalCostSeconds;

    /**
     * 每个 (topK, searchLevel) 组合的 recall 详情列表。
     */
    List<RecallDetail> recallDetails;

    /**
     * 单条 (topK, searchLevel) 组合的 recall 评测结果。
     */
    @Data
    @Builder
    public static class RecallDetail {
        /**
         * TopK 值。
         */
        int topK;

        /**
         * Search level 值。
         */
        int searchLevel;

        /**
         * Recall@K（范围 0.0 ~ 1.0）。
         */
        double recall;

        /**
         * 平均单次搜索延迟（毫秒）。
         */
        double avgLatencyMs;
    }
}
