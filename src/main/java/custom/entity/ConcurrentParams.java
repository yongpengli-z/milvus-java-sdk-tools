package custom.entity;


import lombok.Data;

/**
 * Concurrent（并发执行一组步骤）参数。
 * <p>
 * 前端来源：`customizeComponents.vue` 会把子组件列表组装成对象并塞到 `paramComb`。
 */
@Data
public class ConcurrentParams {
    /**
     * 子流程参数（一个嵌套的 customize_params JSON）。
     * <p>
     * 注意：当前字段类型为 String，通常需要传入 JSON 字符串（或确保反序列化时能转为字符串）。
     * <p>
     * 前端默认值：`{}`（空对象，见 `customizeComponents.vue` 的组装逻辑）
     */
    String paramComb;
}
