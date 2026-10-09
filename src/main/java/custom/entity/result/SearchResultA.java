package custom.entity.result;

import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchResultA{
    CommonResult commonResult;
    int concurrencyNum;
    long requestNum;
    long rpcSuccessNum;
    long rpcFailureNum;
    float requestRps;
    float rpcSuccessRps;
    double passRate;
    float costTime;
    float rps;
    double avg;
    double tp99;
    double tp98;
    double tp90;
    double tp85;
    double tp80;
    double tp50;
    List<List<SearchResp.SearchResult>> searchResults;
    List<String> assertMessages;
}
