package custom.entity;

import lombok.Data;

/**
 * Resume Instance 参数（恢复/启动已停止的 Milvus 实例，Cloud/内部环境）。
 * <p>
 * 对应前端组件：`resumeInstanceEdit.vue`
 */
@Data
public class ResumeInstanceParams {
    /**
     * 实例 ID。
     * <p>
     * 前端：`resumeInstanceEdit.vue` -> "Instance Id"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceId;
}
