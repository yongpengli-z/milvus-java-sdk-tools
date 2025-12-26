package custom.entity;

import io.milvus.v2.common.IndexParam;
import lombok.Data;

/**
 * 单个字段的索引参数（CreateIndexParams.indexParams 的元素）。
 * <p>
 * 对应前端组件：`createIndexEdit.vue` -> IndexParamList 表格行。
 */
@Data
public class IndexParams {
    /**
     * 索引字段名（要对哪个字段建索引）。
     * <p>
     * 前端：`createIndexEdit.vue` -> "FieldName"
     * <p>
     * 前端默认值：`FloatVector_1`（首行默认值；新增行默认空字符串）
     */
    private String fieldName;

    /**
     * 索引类型（Milvus 枚举：`io.milvus.v2.common.IndexParam.IndexType`）。
     * <p>
     * 后端字段名：`indextype`
     * <p>
     * 前端：`createIndexEdit.vue` 使用的 JSON key 是 `indexType`（注意与后端字段名不同）。
     * 如果前端直传 `indexType`，需要确保后端/解析层能映射到该字段，否则会导致索引类型为空。
     * <p>
     * 前端默认值：`AUTOINDEX`
     */
    private IndexParam.IndexType indextype;

    /**
     * 向量度量方式（MetricType，Milvus 枚举：`io.milvus.v2.common.IndexParam.MetricType`）。
     * <p>
     * 前端：`createIndexEdit.vue` -> "MetricType"
     * <p>
     * 前端默认值：`L2`
     */
    private IndexParam.MetricType metricType;

    /**
     * JSON cast 类型（用于 JSON / dynamic field 建索引时的类型转换，大小写不敏感）。
     * <p>
     * 前端：`createIndexEdit.vue` -> "JsonCastType"
     * <p>
     * 常见值：`double` / `bool` / `varchar`
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String jsonCastType;

    /**
     * JSON path（用于 JSON / dynamic field 建索引时指定路径）。
     * <p>
     * 前端：`createIndexEdit.vue` -> "JsonPath"
     * <p>
     * 例：`field["key1"]["key2"]`、`field["key1"][0]["key2"]`
     * <p>
     * 前端默认值：""（空字符串）
     */
    private String jsonPath;

    /**
     * build level（部分向量索引支持；用于控制建索引策略）。
     * <p>
     * 前端：`createIndexEdit.vue` -> "BuildLevel"（0/1/2）
     * <p>
     * 前端默认值：`1`（Balanced）
     */
    private String buildLevel;
}
