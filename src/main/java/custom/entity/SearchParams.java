package custom.entity;

import lombok.Data;

import java.util.List;

@Data
public class SearchParams {
    private String collectionName;
    private int nq;
    private int topK;
    private boolean randomVector;
    private List<String> outputs;
    private String filter;
    private int numConcurrency;
    private long runningMinutes;
    private int searchLevel;
    private String indexAlgo;

}
