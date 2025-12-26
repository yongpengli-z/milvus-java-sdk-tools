package custom.entity;

import lombok.Data;

/**
 * Debug/Test 参数（内部调试用）。
 * <p>
 * 前端当前未在 customize 面板暴露该组件。
 */
@Data
public class DebugTestParams {
    /**
     * 测试名称/标识（由 DebugTestComp 读取并决定执行逻辑）。
     * <p>
     * 默认值：""（空字符串）
     */
    String test;
}
