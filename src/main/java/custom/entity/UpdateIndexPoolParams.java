package custom.entity;

import lombok.Data;

/**
 * Update Index Pool 参数（Cloud/内部环境）。
 * <p>
 * 对应前端组件：`updateIndexPoolEdit.vue`
 */
@Data
public class UpdateIndexPoolParams {
    /**
     * Manager 镜像 tag（可选）。
     * <p>
     * 前端：`updateIndexPoolEdit.vue` -> "ManagerImageTag"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String managerImageTag;

    /**
     * Worker 镜像 tag（可选；支持 `latest-release`）。
     * <p>
     * 前端：`updateIndexPoolEdit.vue` -> "WorkerImageTag"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String workerImageTag;

    /**
     * IndexClusterId。
     * <p>
     * 前端：`updateIndexPoolEdit.vue` -> "IndexClusterId"
     * <p>
     * 注意：前端下拉框返回的是字符串形式的 id，需要保证最终 JSON 能解析为 int。
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 建议：生成 JSON 时 indexClusterId 传数字（或不传）。
     */
    int indexClusterId;
}
