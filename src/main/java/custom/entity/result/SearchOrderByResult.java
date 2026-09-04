package custom.entity.result;

import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchOrderByResult {
    private CommonResult commonResult;
    private List<List<SearchResp.SearchResult>> searchResults;
    private List<String> assertMessages;
}
