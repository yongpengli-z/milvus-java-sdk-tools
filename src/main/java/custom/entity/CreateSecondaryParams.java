package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * 为已有实例添加 Secondary 集群参数。
 * <p>
 * 对应 RM 接口：POST /resource/v1/global_cluster/milvus/create_secondary
 * <p>
 * 两种场景：
 * <ul>
 *   <li>场景 A：已有普通实例 → 传 instanceId + secondaryClusters → 自动转为 Global Cluster</li>
 *   <li>场景 B：已有 Global Cluster → 传 globalClusterId + secondaryClusters → 添加新 secondary</li>
 * </ul>
 */
@Data
public class CreateSecondaryParams {

    // ==================== 账号相关（可选，留空使用默认登录） ====================

    String accountEmail;
    String accountPassword;

    // ==================== 目标实例标识 ====================

    /** 已有普通实例 ID（场景 A：standalone → Global Cluster） */
    String instanceId;

    /** 已有 Global Cluster ID（场景 B：扩展 secondary） */
    String globalClusterId;

    // ==================== Secondary 实例列表 ====================

    /** 要添加的 secondary 集群列表 */
    List<SecondaryCluster> secondaryClusters;

    /** 是否启用子 JobCenter，默认 false */
    boolean enableChildJobCenter;

    // ==================== 轮询配置 ====================

    /** 轮询等待超时（分钟），默认 30 */
    int waitTimeoutMinutes = 30;

    @Data
    public static class SecondaryCluster {
        /** secondary 所在 regionId */
        String regionId;
        /** secondary 实例名称 */
        String instanceName;
        /** secondary CU 类型 */
        String classId;
        /** secondary 副本数 */
        Integer replica;
    }
}
