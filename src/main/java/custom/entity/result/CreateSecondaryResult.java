package custom.entity.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class CreateSecondaryResult {
    CommonResult commonResult;
    /** Global Cluster ID（新转换或已有的） */
    String globalClusterId;
    /** Primary 实例 ID */
    String primaryInstanceId;
    /** Primary 实例 ConnectAddress */
    String primaryUri;
    /** 新增的 Secondary 实例 ID 列表 */
    List<String> newSecondaryInstanceIds;
    /** 新增的 Secondary 实例 URI 映射：instanceId -> ConnectAddress */
    Map<String, String> newSecondaryUris;
    /** Global Endpoint（global-cluster 统一入口） */
    String globalEndpoint;
    /** 创建耗时（秒） */
    int createCostSeconds;
}
