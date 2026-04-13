package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CreateGlobalClusterResult {
    CommonResult commonResult;
    /** Global Cluster ID */
    String globalClusterId;
    /** Primary 实例 ID */
    String instanceId;
    /** Primary 实例 ConnectAddress */
    String primaryUri;
    /** Secondary 实例 ID 列表 */
    List<String> secondaryInstanceIds;
    /** Secondary 实例 URI 映射：instanceId -> ConnectAddress */
    Map<String, String> secondaryUris;
    /** 创建耗时（秒） */
    int createCostSeconds;
}
