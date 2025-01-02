package custom.entity;

import lombok.Data;

import java.util.List;

@Data
public class SearchIteratorParams {
    private String collectionName;
    private int nq;
    private int topK;
    private boolean randomVector;
    private String vectorFieldName;
    private List<String> outputs;
    private String filter;
    private String metricType;
    private int numConcurrency;
    private long runningMinutes;
    private String params;
    private String indexAlgo;
    private int batchSize;
    private boolean useV1;
    private String annsFields;
}
