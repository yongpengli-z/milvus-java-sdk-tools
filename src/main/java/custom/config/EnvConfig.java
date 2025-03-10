package custom.config;

import lombok.Data;

@Data
public class EnvConfig {
    String regionId;
    String rmHost;
    String cloudServiceHost;
    String cloudServiceTestHost;
    String cloudOpsServiceHost;
    String cloudOpsServiceToken;
    String infraHost;
    String infraToken;
}
