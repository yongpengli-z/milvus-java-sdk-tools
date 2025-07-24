package custom.entity;

import io.milvus.v2.common.IndexParam;
import lombok.Data;

@Data
public class IndexParams {
    private String fieldName;
    private  IndexParam.IndexType indextype;
    private IndexParam.MetricType metricType;
    private String jsonCastType;
    private String jsonPath;
    private String buildLevel;
}
