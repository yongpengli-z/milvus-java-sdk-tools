package custom.entity;

import lombok.Data;

/**
 * 创建 Milvus 实例参数（Cloud/内部环境）。
 * <p>
 * 对应前端组件：`createInstanceEdit.vue`
 */
@Data
public class CreateInstanceParams {
    /**
     * DB Version（镜像版本）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "DB Version"
     * <p>
     * 支持特殊值：`latest-release`
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String dbVersion;

    /**
     * CU 类型（规格），例如 `class-1-enterprise`。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "CU Type"
     * <p>
     * 前端默认值：`class-1-enterprise`
     */
    String cuType;

    /**
     * 实例名称（instanceName）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Instance Name"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceName;

    /**
     * 架构：1=AMD，2=ARM。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Architecture"
     * <p>
     * 前端默认值：2（ARM）
     */
    int architecture;

    /**
     * 实例类型（instanceType）。
     * <p>
     * 前端：`createInstanceEdit.vue` 未直接暴露枚举含义（通常保持默认）。
     * <p>
     * 前端默认值：1
     */
    int instanceType;

    /**
     * 创建实例所使用的账号邮箱（可选）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Account Email"
     * <p>
     * 留空：后端会使用默认/临时账号登录并创建实例（具体见 CreateInstanceComp 的账号检查逻辑）。
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountEmail;

    /**
     * 创建实例所使用的账号密码（可选）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Account Password"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String accountPassword;

    /**
     * 实例副本数（replica）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Replica"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：1
     */
    int replica;

    /**
     * root/db_admin 的密码（用于后续连接 token 生成）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Instance Root Password"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：`Milvus123`
     */
    String rootPassword;

    /**
     * 创建完成后使用哪个角色连接实例。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "RoleUse"
     * <p>
     * 常用：`root` / `db_admin`
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：`root`
     */
    String roleUse;

    /**
     * 使用时长（小时），用于实例生命周期管理（可选）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Use Hours"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：0
     */
    int useHours;

    /**
     * 是否重保（dev ops 提供）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "BizCritical"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    boolean bizCritical;

    /**
     * 是否独占（dev ops 提供）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Update QN Monopoly"
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：false
     */
    boolean monopolized;

    /**
     * Streaming node 临时配置（dev ops 提供修改 sn 的配置；可选）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Modify streaming node config"
     * <p>
     * 前端默认值：`{replicaNum:"", cpu:"", memory:"", disk:""}`
     */
    StreamingNodeParams streamingNodeParams;
}
