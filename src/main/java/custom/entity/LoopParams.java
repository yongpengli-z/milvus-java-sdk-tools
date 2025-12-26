package custom.entity;

import lombok.Data;

/**
 * Loop（循环执行一组步骤）参数。
 * <p>
 * 对应前端组件：`loopEdit.vue`
 */
@Data
public class LoopParams {
    /**
     * 子流程参数（一个嵌套的 customize_params JSON）。
     * <p>
     * 前端：`customizeComponents.vue` 会把子组件列表组装成对象并塞到 `paramComb`。
     * <p>
     * 前端默认值：`{}`（空对象）
     * <p>
     * 注意：当前字段类型为 String，通常需要传入 JSON 字符串（或确保反序列化时能转为字符串）。
     */
    String paramComb;

    /**
     * 循环总时长上限（分钟）。
     * <p>
     * 前端：`loopEdit.vue` -> "Running Time(Minutes)"
     * <p>
     * 前端默认值：0
     */
    int runningMinutes;

    /**
     * 循环次数上限（cycleNum）。
     * <p>
     * 前端：`loopEdit.vue` -> "Cycle Num"
     * <p>
     * 前端默认值：1
     */
    int cycleNum;
}
