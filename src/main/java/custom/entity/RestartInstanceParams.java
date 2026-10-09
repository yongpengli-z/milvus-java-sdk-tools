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
}
