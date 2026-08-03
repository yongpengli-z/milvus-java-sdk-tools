package custom.entity.result;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UpdateWoodpeckerImageResult {
    CommonResult commonResult;
    String regionId;
    String woodpeckerId;
    String newImageTag;
    Long processInstanceId;
}
