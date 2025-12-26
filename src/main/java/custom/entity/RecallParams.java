package custom.entity;

import lombok.Data;

/**
 * Recall 参数（用于计算不同 search level 下的 recall/命中率）。
 * <p>
 * 前端当前未提供对应的 Edit 表单（`customize/components/items/` 下无 recallEdit）。
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
     * Search level（会写入 searchParams: {"level": x}）。
     * <p>
     * 默认值：1
     */
    private int searchLevel;

    /**
     * 向量字段名（annsField）。
     * <p>
     * 默认值：`FloatVector_1`
     */
    String annsField;
}
