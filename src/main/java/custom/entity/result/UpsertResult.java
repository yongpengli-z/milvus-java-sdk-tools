package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpsertResult {
    CommonResult commonResult;
    long numEntries;
    double costTime;
    long requestNum;
    double rps;
}
