package custom.entity;

import lombok.Data;

import java.util.List;

@Data
public class ModifyParams {
    String instanceId;
    boolean needRestart;
    List<Params> paramsList;
    String accountEmail;
    String accountPassword;

    @Data
    public static class Params {
        String paramName;
        String paramValue;
    }
}
