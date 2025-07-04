package custom.entity;

import lombok.Data;

@Data
public class AlterInstanceIndexClusterParams {
    String instanceId;
    int indexClusterId;
}
