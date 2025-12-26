package custom.entity;

import lombok.Data;

/**
 * 启动初始化参数（initial_params）。
 * <p>
 * 前端入口：`combination.vue` -> Setting 面板 -> "pre clean instance"
 */
@Data
public class InitialParams {
    /**
     * 是否在启动时清理实例内所有 collection。
     * <p>
     * true：启动后会列出并 drop 所有 collection（见 `InitialComp.initialRunning`）。
     * <p>
     * 默认值（前端）：false
     */
    boolean cleanCollection;
}
