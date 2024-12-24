package custom.entity;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.Data;

import java.util.List;

@Data
public class LoadParams {
    private boolean loadAll;
    private String collectionName;
    private List<String> loadFields;
    private boolean skipLoadDynamicField;

}
