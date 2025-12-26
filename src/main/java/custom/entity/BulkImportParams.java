package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Bulk Import 参数（批量导入）。
 * <p>
 * 前端当前未在 customize 面板暴露该组件。
 */
@Data
public class BulkImportParams {
    /**
     * 文件路径列表（二维数组）。
     * <p>
     * 通常按 batch/组组织，每组内为多个文件路径。
     * <p>
     * 默认值：[]（空数组）
     */
    List<List<String>> filePaths;

    /**
     * Collection 名称。
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     * <p>
     * 默认值：""（空字符串）
     */
    String collectionName;

    /**
     * Partition 名称（可选）。
     * <p>
     * 默认值：""（空字符串）
     */
    String partitionName;

    /**
     * 数据集类型/标识（由导入逻辑解释）。
     * <p>
     * 默认值：`random`
     */
    String dataset;
}
