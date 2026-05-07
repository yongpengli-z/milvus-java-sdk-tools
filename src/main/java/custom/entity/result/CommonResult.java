package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CommonResult {
    String result;
    String message;

    public static void markWarningIfAssertFail(CommonResult commonResult, List<String> assertMessages) {
        if (commonResult == null || assertMessages == null) {
            return;
        }
        if (!ResultEnum.SUCCESS.result.equals(commonResult.getResult())) {
            return;
        }
        boolean hasAssertFail = assertMessages.stream()
                .anyMatch(message -> message != null && message.contains("[ASSERT FAIL]"));
        if (hasAssertFail) {
            commonResult.setResult(ResultEnum.WARNING.result);
        }
    }
}
