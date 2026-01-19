package custom.entity;

import lombok.Data;

/**
 * Helm 依赖组件配置（etcd/MinIO/Pulsar/Kafka）。
 * <p>
 * 用于配置 Milvus 的外部依赖组件，支持使用内置组件或外部已有服务。
 */
@Data
public class HelmDependencyConfig {

    /**
     * 是否使用外部服务。
     * <p>
     * true：使用外部已有的服务（需要配置 externalEndpoints 等）
     * false：使用 Helm Chart 内置的服务
     * <p>
     * 前端默认值：false
     */
    boolean useExternal;

    /**
     * 是否启用内置服务（当 useExternal=false 时有效）。
     * <p>
     * 对于 Standalone 模式，通常禁用 Pulsar。
     * <p>
     * 前端默认值：true
     */
    boolean enabled;

    /**
     * 外部服务 Endpoints（当 useExternal=true 时使用）。
     * <p>
     * 示例值：
     * - etcd: `etcd-0.etcd:2379,etcd-1.etcd:2379`
     * - MinIO: `minio.minio:9000`
     * - Pulsar: `pulsar-proxy.pulsar:6650`
     * - Kafka: `kafka-0.kafka:9092,kafka-1.kafka:9092`
     * <p>
     * 前端默认值：""
     */
    String externalEndpoints;

    /**
     * 外部服务访问 Key/用户名（如 MinIO accessKey）。
     * <p>
     * 前端默认值：""
     */
    String accessKey;

    /**
     * 外部服务访问 Secret/密码（如 MinIO secretKey）。
     * <p>
     * 前端默认值：""
     */
    String secretKey;

    /**
     * Bucket 名称（仅用于 MinIO/S3）。
     * <p>
     * 前端默认值：`milvus-bucket`
     */
    String bucketName;

    /**
     * Root Path（用于 etcd/MinIO 的数据隔离）。
     * <p>
     * 前端默认值：`milvus`
     */
    String rootPath;

    /**
     * 内置服务的副本数（当 useExternal=false 时）。
     * <p>
     * 前端默认值：1
     */
    int replicaCount;

    /**
     * 存储大小（例如 `10Gi`）。
     * <p>
     * 前端默认值：`10Gi`
     */
    String storageSize;

    /**
     * 存储类名（StorageClass，可选）。
     * <p>
     * 如果为空，使用集群默认 StorageClass。
     * <p>
     * 前端默认值：""
     */
    String storageClassName;

    /**
     * 是否使用 IAM 认证（仅用于外部 S3 存储）。
     * <p>
     * true：使用云平台 IAM 角色认证（如 AWS IAM Role、GCP Workload Identity、Azure Managed Identity）
     * false：使用 accessKey/secretKey 认证
     * <p>
     * 使用 IAM 时，accessKey 和 secretKey 可以为空。
     * <p>
     * 前端默认值：false
     */
    boolean useIAM;

    /**
     * IAM Endpoint（仅当 useIAM=true 时可选配置）。
     * <p>
     * 用于自定义 IAM 服务端点，通常在私有云或特殊网络环境下使用。
     * 如果为空，使用云平台默认的 IAM 端点。
     * <p>
     * 前端默认值：""
     */
    String iamEndpoint;

    /**
     * 云存储区域（Region）。
     * <p>
     * 用于指定云存储的区域，某些云平台（如 AWS S3）需要此配置。
     * <p>
     * 示例值：
     * - AWS: us-west-2, us-east-1
     * - Aliyun: cn-hangzhou, cn-beijing
     * <p>
     * 前端默认值：""
     */
    String region;
}
