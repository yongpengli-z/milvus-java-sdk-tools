package custom.pojo;

import lombok.Data;

/**
 * Partial Update 时需要更新的字段名。
 * <p>
 * 对应前端 upsertEdit.vue 中 updateFieldNames 列表的每一项。
 */
@Data
public class UpdateFieldName {
    /**
     * 字段名称，对应 collection schema 中的字段名。
     */
    private String fieldName;
}
