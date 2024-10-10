package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchResultA{
    CommonResult commonResult;
    int concurrencyNum;
    long requestNum;
    double passRate;
    double costTime;
    double rps;
    double avg;
    double tp99;
    double tp98;
    double tp90;
    double tp85;
    double tp80;
    double tp50;
}
