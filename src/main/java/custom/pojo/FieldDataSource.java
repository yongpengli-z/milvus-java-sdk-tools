package custom.pojo;

import lombok.Data;

/**
 * 字段级别数据源配置。
 * <p>
 * 指定某个字段从哪个数据集读取数据，而非使用默认的 random 生成。
 * <p>
 * 示例 JSON 配置：
 * <pre>
 * {
 *   "fieldName": "json_field",
 *   "dataset": "custom_json"
 * }
 * </pre>
 * <p>
 * 未配置的字段默认使用 random 生成。
 */
@Data
public class FieldDataSource {
    /**
     * 字段名称，对应 collection schema 中的字段名。
     */
    private String fieldName;

    /**
     * 数据集名称，对应 DatasetEnum 中的名称（如 "custom_json", "gist", "sift" 等）。
     */
    private String dataset;
}
