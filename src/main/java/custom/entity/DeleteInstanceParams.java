package custom.entity;

import lombok.Data;

@Data
public class DeleteInstanceParams {
    String instanceId;
    boolean useOPSTestApi;
}
