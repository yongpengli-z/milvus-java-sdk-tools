package custom.entity;

import lombok.Data;

/**
 * Restart Instance 参数（重启 Milvus 实例，Cloud/内部环境）。
 * <p>
 * 对应前端组件：`restartInstanceEdit.vue`
 */
@Data
public class RestartInstanceParams {
    /**
     * 实例 ID。
     * <p>
     * 前端：`restartInstanceEdit.vue` -> "Instance Id"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceId;

    /**
     * 账号邮箱（可选）。
     * <p>
     * 前端：`restartInstanceEdit.vue` -> "Account Email"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountEmail;

    /**
     * 账号密码（可选）。
     * <p>
     * 前端：`restartInstanceEdit.vue` -> "Account Password"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountPassword;
}
