package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AssertResult {
    private CommonResult commonResult;
    private int totalAssertions;
    private int passedAssertions;
    private int failedAssertions;
    private List<AssertItemResult> assertionResults;
    private List<String> assertMessages;

    @Data
    @Builder
    public static class AssertItemResult {
        private String type;
        private String metric;
        private String operator;
        private Object expected;
        private Object actual;
        private boolean passed;
        private String message;
        private Map<String, Object> details;
    }
}
