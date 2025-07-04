package custom.entity.result;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlterInstanceIndexClusterResult {
    CommonResult commonResult;
    String instanceId;
    int currentIndexClusterId;
}
