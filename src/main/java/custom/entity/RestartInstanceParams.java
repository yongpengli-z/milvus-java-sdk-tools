package custom.entity;

import lombok.Data;

@Data
public class RestartInstanceParams {
    String instanceId;
    String accountEmail;
    String accountPassword;
}
