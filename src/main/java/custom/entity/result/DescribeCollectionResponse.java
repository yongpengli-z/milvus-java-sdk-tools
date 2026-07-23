package custom.entity.result;

import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Serializable view of the Milvus SDK response.
 *
 * <p>The SDK response embeds Gson's {@code JsonObject} in its collection schema.
 * It must not be returned directly because task results are serialized with Fastjson.</p>
 */
@Data
@Builder
public class DescribeCollectionResponse {
    String collectionName;
    Long collectionID;
    String databaseName;
    String description;
    Long numOfPartitions;
    List<String> fieldNames;
    List<String> vectorFieldNames;
    String primaryFieldName;
    Boolean enableDynamicField;
    Boolean autoID;
    String collectionSchema;
    Long createTime;
    Long createUtcTime;
    String consistencyLevel;
    Integer shardsNum;
    Map<String, String> properties;

    public static DescribeCollectionResponse from(DescribeCollectionResp response) {
        return DescribeCollectionResponse.builder()
                .collectionName(response.getCollectionName())
                .collectionID(response.getCollectionID())
                .databaseName(response.getDatabaseName())
                .description(response.getDescription())
                .numOfPartitions(response.getNumOfPartitions())
                .fieldNames(response.getFieldNames())
                .vectorFieldNames(response.getVectorFieldNames())
                .primaryFieldName(response.getPrimaryFieldName())
                .enableDynamicField(response.getEnableDynamicField())
                .autoID(response.getAutoID())
                .collectionSchema(response.getCollectionSchema() == null ? null : response.getCollectionSchema().toString())
                .createTime(response.getCreateTime())
                .createUtcTime(response.getCreateUtcTime())
                .consistencyLevel(response.getConsistencyLevel() == null ? null : response.getConsistencyLevel().name())
                .shardsNum(response.getShardsNum())
                .properties(response.getProperties())
                .build();
    }
}
