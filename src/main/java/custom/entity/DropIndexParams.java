package custom.entity;

import lombok.Data;

/**
 * Drop Index 参数（删除指定字段的索引）。
 * <p>
 * 对应前端组件：`dropIndexEdit.vue`
 */
@Data
public class DropIndexParams {
    /**
     * Collection 名称。
     * <p>
     * 前端：`dropIndexEdit.vue` -> "Collection Name"
     * <p>
     * 前端默认值：""（空字符串）
     * <p>
     * 为空时：后端默认使用最近一次创建/记录的 collection。
     */
    String collectionName;

    /**
     * 要 drop 索引的字段名。
     * <p>
     * 前端：`dropIndexEdit.vue` -> "Field Name"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String fieldName;
}
