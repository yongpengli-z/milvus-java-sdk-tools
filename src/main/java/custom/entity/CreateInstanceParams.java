package custom.entity;

import lombok.Data;

@Data
public class CreateInstanceParams {
    String dbVersion;
    String cuType;
    String instanceName;
    int architecture;
    int instanceType;
    String accountEmail;
    String accountPassword;
    int replica;
    String rootPassword;
    String roleUse;
    int useHours;
    // dev ops 提供的独占label
    boolean bizCritical;
}
