package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchIteratorResultA {
    CommonResult commonResult;
    int concurrencyNum;
    long requestNum;
    float passRate;
    float costTime;
    float rps;
    double avg;
    double tp99;
    double tp98;
    double tp90;
    double tp85;
    double tp80;
    double tp50;
}
