package custom.entity;

import lombok.Data;

@Data
public class UpdateIndexPoolParams {
    String managerImageTag;
    String workerImageTag;
}
