package custom.entity;

import io.milvus.common.clientenum.FunctionType;
import lombok.Data;

import java.util.List;

/**
 * Collection Function（例如 BM25）配置。
 * <p>
 * 对应前端组件：`createCollectionEdit.vue` -> "Add Function"
 */
@Data
public class FunctionParams {
    /**
     * Function 类型（Milvus 枚举 `io.milvus.common.clientenum.FunctionType`）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "FunctionType"
     * <p>
     * 例：`BM25`
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 建议：生成 JSON 时 functionType 为空请使用 `null` 或直接不传，避免 enum 解析失败。
     */
    FunctionType functionType;

    /**
     * Function 名称（必填，不能为空字符串）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "Name"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String name;

    /**
     * 输入字段名列表（InputFieldNames）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "InputFieldNames"（逗号分隔输入）
     * <p>
     * 前端默认值：[]（空数组）
     */
    List<String> inputFieldNames;

    /**
     * 输出字段名列表（OutputFieldNames）。
     * <p>
     * 前端：`createCollectionEdit.vue` -> "OutputFieldNames"（逗号分隔输入）
     * <p>
     * 前端默认值：[]（空数组）
     */
    List<String> outputFieldNames;
}
