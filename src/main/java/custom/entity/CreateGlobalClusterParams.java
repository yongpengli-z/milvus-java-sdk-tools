package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * 创建 Global Cluster 参数。
 * <p>
 * 对应 RM 接口：POST /resource/v1/global_cluster/milvus/create
 * <p>
 * 请求体对应 activity-provider 的 CreateGlobalInstanceRequest。
 */
@Data
public class CreateGlobalClusterParams {

    // ==================== 账号相关（可选，留空使用默认登录） ====================

    String accountEmail;
    String accountPassword;

    // ==================== Primary 实例参数 ====================

    /** 主实例所在 regionId，例如 aws-us-west-2 */
    String regionId;

    /** DB Version（镜像版本），支持 latest-release / nightly / 具体版本 */
    String dbVersion;

    /** CU 类型（规格），例如 class-1-enterprise */
    String classId;

    /** 副本数，默认 1 */
    int replica = 1;

    /** 实例名称 */
    String instanceName;

    /** 实例类型，默认 1 */
    int instanceType = 1;

    /** 架构：1=AMD，2=ARM */
    int architecture = 2;

    /** root 密码 */
    String rootPwd;

    /** 实例描述 */
    String instanceDescription = "create by java tools";

    /** 组织类型，默认 SAAS */
    String orgType = "SAAS";

    // ==================== Secondary 实例列表 ====================

    /** secondary 集群列表 */
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
