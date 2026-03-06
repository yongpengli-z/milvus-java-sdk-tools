package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DescribeAliasResult {
    CommonResult commonResult;
    String alias;
    String collectionName;
    String databaseName;
}
