package custom.entity;

import lombok.Data;

/**
 * Delete Instance 参数（删除 Milvus 实例，Cloud/内部环境）。
 * <p>
 * 对应前端组件：`deleteInstanceEdit.vue`
 */
@Data
public class DeleteInstanceParams {
    /**
     * 实例 ID。
     * <p>
     * 前端：`deleteInstanceEdit.vue` -> "Instance Id"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceId;

    /**
     * 是否使用 OPS-Test API 删除实例。
     * <p>
     * 后端字段名：`useOPSTestApi`
     * <p>
     * 前端：`deleteInstanceEdit.vue` 使用的 JSON key 是 `useCloudTestApi`（注意与后端字段名不同）。
     * <p>
     * 前端必填：是（useCloudTestApi）
     * <p>
     * 前端默认值：false
     */
    boolean useOPSTestApi;

    /**
     * 账号邮箱（可选；用于指定“在哪个账号下”执行删除）。
     * <p>
     * 前端：`deleteInstanceEdit.vue` -> "Account Email"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountEmail;

    /**
     * 账号密码（可选）。
     * <p>
     * 前端：`deleteInstanceEdit.vue` -> "Account Password"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountPassword;
}
