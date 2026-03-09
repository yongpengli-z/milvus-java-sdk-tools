package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UpsertResult {
    CommonResult commonResult;
    long numEntries;
    double costTime;
    long requestNum;
    double rps;
    List<String> assertMessages;
}
