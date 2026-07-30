package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Result recorded for a Chaos Mesh scenario step. */
@Data
@Builder
public class ChaosMeshResult {
    String operation;
    String kind;
    String namespace;
    String name;
    List<String> affectedPods;
    CommonResult commonResult;
}
