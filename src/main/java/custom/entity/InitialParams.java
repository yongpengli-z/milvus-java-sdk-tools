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

    /**
     * 全局日志级别（运行时动态设置）。
     * <p>
     * 可选值：DEBUG、INFO、WARN、ERROR。
     * 设为 DEBUG 可查看 DatasetUtil 每批次的数据集文件读取详情；设为 WARN/ERROR 可减少日志输出。
     * <p>
     * 默认值："INFO"
     */
    String logLevel = "INFO";
}
