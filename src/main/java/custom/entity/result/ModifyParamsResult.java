package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class ModifyParamsResult {
    CommonResult commonResult;
    List<Params> paramsList;

    @Data
    @Builder
    public static class Params{
        String paramName;
        String currentValue;

    }
}
