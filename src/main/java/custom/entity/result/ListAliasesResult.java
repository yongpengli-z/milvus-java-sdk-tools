package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ListAliasesResult {
    CommonResult commonResult;
    String collectionName;
    List<String> aliases;
}
