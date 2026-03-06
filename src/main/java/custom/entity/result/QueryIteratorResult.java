package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QueryIteratorResult {
    CommonResult commonResult;
    long requestNum;
    float costTime;
    float rps;
    float passRate;
    int concurrencyNum;
    double avg;
    double tp99;
    double tp98;
    double tp90;
    double tp85;
    double tp80;
    double tp50;
}
