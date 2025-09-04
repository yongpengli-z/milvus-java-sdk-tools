package custom.entity;

import custom.pojo.GeneralDataRole;
import lombok.Data;

import java.util.List;

@Data
public class QueryParams {
    private String collectionName;
    private List<String> outputs;
    private String filter;
    private List<Object> ids;
    private int numConcurrency;
    private long runningMinutes;
    private long limit;
    private List<String> partitionNames;
    private long offset;
    private List<GeneralDataRole> generalFilterRoleList;
}
