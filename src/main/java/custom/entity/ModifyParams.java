package custom.entity;

import lombok.Data;

import java.util.List;

@Data
public class ModifyParams {
    public String instanceId;
    public boolean needRestart;
    public List<params> paramsList;

    @Data
    public static class params{
        String paramName;
        String paramValue;
    }
}
