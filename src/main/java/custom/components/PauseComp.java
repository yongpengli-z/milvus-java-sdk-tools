package custom.components;

import custom.common.ComponentSchedule;
import custom.common.TaskStatusEnum;
import custom.entity.PauseParams;
import custom.entity.result.CommonResult;
import custom.entity.result.PauseResult;
import custom.entity.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PauseComp {
    public static PauseResult pause(PauseParams pauseParams) {
        String reason = pauseParams == null ? null : pauseParams.getReason();
        String qtpResponse = "";
        int resumeStatus = TaskStatusEnum.RUNNING.status;
        CommonResult commonResult;
        try {
            log.info("Pause current customize task, reason={}", reason);
            qtpResponse = ComponentSchedule.pauseTaskByQtpServer();
            resumeStatus = ComponentSchedule.waitIfTaskPaused();
            if (resumeStatus == TaskStatusEnum.TERMINATE.status) {
                commonResult = CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message("Task was terminated while paused")
                        .build();
            } else {
                commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
            }
        } catch (Exception e) {
            log.warn("Pause exception: {}", e.getMessage());
            commonResult = CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message(e.getMessage())
                    .build();
        }
        return PauseResult.builder()
                .reason(reason)
                .qtpResponse(qtpResponse)
                .resumeStatus(resumeStatus)
                .commonResult(commonResult)
                .build();
    }
}
