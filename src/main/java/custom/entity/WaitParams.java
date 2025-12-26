package custom.entity;

import lombok.Data;

/**
 * Wait 参数（睡眠等待一段时间）。
 * <p>
 * 对应前端组件：`waitEdit.vue`
 */
@Data
public class WaitParams {
    /**
     * 等待时长（分钟）。
     * <p>
     * 前端：`waitEdit.vue` -> "Wait Minutes"
     * <p>
     * 前端默认值：1
     */
    long waitMinutes;
}
