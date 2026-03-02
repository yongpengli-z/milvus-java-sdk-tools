package custom.entity;

import lombok.Data;

/**
 * Scale Instance 参数（升降配 Milvus 实例，Cloud 环境）。
 * <p>
 * 对应前端组件：`scaleInstanceEdit.vue`
 */
@Data
public class ScaleInstanceParams {
    /**
     * 实例 ID（可选，留空则使用当前创建的实例 ID）。
     * <p>
     * 前端：`scaleInstanceEdit.vue` -> "Instance Id"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceId;

    /**
     * 目标 CU 类型（classId），例如 `class-2-enterprise`。
     * <p>
     * 前端：`scaleInstanceEdit.vue` -> "Target CU Type"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String targetCuType;

    /**
     * 目标副本数（replica）。
     * <p>
     * 0 表示不修改 replica。
     * <p>
     * 前端：`scaleInstanceEdit.vue` -> "Target Replica"
     * <p>
     * 前端默认值：0
     */
    int targetReplica;

    /**
     * 账号邮箱（可选）。
     * <p>
     * 前端：`scaleInstanceEdit.vue` -> "Account Email"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountEmail;

    /**
     * 账号密码（可选）。
     * <p>
     * 前端：`scaleInstanceEdit.vue` -> "Account Password"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountPassword;
}
