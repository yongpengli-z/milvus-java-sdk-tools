package custom.entity;

import lombok.Data;

/**
 * RollingUpgrade 参数（滚动升级 Milvus 实例，Cloud/内部环境）。
 * <p>
 * 对应前端组件：`rollingUpdateEdit.vue`
 */
@Data
public class RollingUpgradeParams {
    /**
     * 目标实例 ID（可选）。
     * <p>
     * 留空 = 当前任务上下文里的实例（newInstanceInfo）；
     * 可对任意已有实例升级：in01 走滚动升级，in06 升 kite-coordinator，in07 升 QueryNode。
     * <p>
     * 前端：`rollingUpdateEdit.vue` -> "Instance ID"
     * <p>
     * 前端必填：否
     * <p>
     * 前端默认值：""（空字符串）
     */
    String instanceId;

    /**
     * 目标 DB Version（镜像版本）。
     * <p>
     * 前端：`rollingUpdateEdit.vue` -> "Target DbVersion"
     * <p>
     * 支持特殊值：`latest-release`
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""（空字符串）
     */
    String targetDbVersion;

    /**
     * 是否强制重启（forceRestart）。
     * <p>
     * 前端：`rollingUpdateEdit.vue` -> "Force Restart"
     * <p>
     * 前端默认值：true
     */
    boolean forceRestart;
}
