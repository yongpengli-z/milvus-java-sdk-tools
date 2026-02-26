package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Recall 评测参数（多维度 recall 评测：多 topK x 多 searchLevel 组合）。
 * <p>
 * 通过 brute force search（高 search level）获取 ground truth，
 * 然后在不同 (topK, searchLevel) 组合下计算 recall@K。
 */
@Data
public class RecallParams {
    /**
     * Collection 名称。
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     * <p>
     * 默认值：""（空字符串）
     */
    private String collectionName;

    /**
     * 向量字段名（annsField）。
     * <p>
     * 支持正常向量字段或 struct 中的向量字段（格式：structFieldName[subFieldName]）。
     * <p>
     * 默认值："FloatVector_1"
     */
    private String annsField;

    /**
     * NQ（query 向量数量，从采样数据中随机挑选 nq 条用于评测）。
     * <p>
     * 默认值：10
     */
    private int nq;

    /**
     * TopK 列表（评测多个 topK 值下的 recall）。
     * <p>
     * 示例：[1, 10, 50, 100]
     * <p>
     * 默认值：[1]
     */
    private List<Integer> topKList;

    /**
     * Search Level 列表（评测多个 search level 下的 recall）。
     * <p>
     * 示例：[1, 2, 3, 5]
     * <p>
     * 默认值：[1]
     */
    private List<Integer> searchLevelList;

    /**
     * Ground truth 所使用的 search level（brute force）。
     * <p>
     * 默认值：10
     */
    private int groundTruthLevel;

    /**
     * 标量过滤表达式（可选）。
     * <p>
     * 默认值：""（空字符串，不过滤）
     */
    private String filter;

    /**
     * 从 collection 中采样的向量数量（用于构建查询向量池）。
     * <p>
     * 默认值：1000
     */
    private int sampleNum;

    /**
     * 一致性级别。
     * <p>
     * 默认值："BOUNDED"
     */
    private String consistencyLevel;
}
