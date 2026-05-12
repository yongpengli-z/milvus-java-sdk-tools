package custom.entity;

import lombok.Data;

/**
 * Pause 参数（主动暂停当前自定义任务，等待页面恢复）。
 */
@Data
public class PauseParams {
    /**
     * 暂停原因，仅用于结果记录。
     */
    String reason;
}
