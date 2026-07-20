package custom.entity.result;

import lombok.Builder;
import lombok.Data;

/** Result recorded for a Chaos Mesh scenario step. */
@Data
@Builder
public class ChaosMeshResult {
    String operation;
    String kind;
    String namespace;
    String name;
    boolean dryRun;
    Object resource;
    CommonResult commonResult;
}
