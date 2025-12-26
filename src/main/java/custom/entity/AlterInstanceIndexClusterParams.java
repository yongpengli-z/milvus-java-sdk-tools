package custom.entity;

import lombok.Data;

/**
 * Alter Instance Index Cluster 参数（切换实例使用的 index cluster，Cloud/内部环境）。
 * <p>
 * 对应前端组件：`alterInstanceIndexClusterEdit.vue`
 */
@Data
public class AlterInstanceIndexClusterParams {
    /**
     * 实例 ID（可选；为空时后端可能使用当前任务实例）。
     * <p>
     * 前端：`alterInstanceIndexClusterEdit.vue` -> "instanceId"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceId;

    /**
     * IndexClusterId。
     * 不同云上环境的池子对应不同的id，比如aws对应127，gcp对应99，azure对应96，ali对应94，hwc对应100
     * <p>
     * 前端：`alterInstanceIndexClusterEdit.vue` -> "IndexClusterId"
     * <p>
     * 注意：前端下拉框返回的是字符串形式的 id，真正传入后端为对应的id(int类型)。
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 建议：生成 JSON 时 indexClusterId 传数字（或不传）。
     */
    int indexClusterId;
}
