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
