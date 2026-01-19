package custom.entity;

import lombok.Data;

/**
 * Helm 依赖组件配置（etcd/MinIO/Pulsar/Kafka）。
 * <p>
 * 用于配置 Milvus 的外部依赖组件，支持使用内置组件或外部已有服务。
 * <p>
 * <h2>外部 S3 存储认证方式</h2>
 * 当 useExternal=true 时，支持两种认证方式：
 * <p>
 * <h3>方式一：AK/SK 认证（useIAM=false）</h3>
 * 使用 accessKey 和 secretKey 进行认证。
 * <p>
 * <b>必填字段：</b>
 * <ul>
 *   <li>useExternal = true</li>
 *   <li>useIAM = false（或不设置，默认 false）</li>
 *   <li>externalEndpoints - 存储服务端点（如 buckettestazure.blob.core.windows.net）</li>
 *   <li>accessKey - 访问密钥</li>
 *   <li>secretKey - 密钥</li>
 *   <li>bucketName - 桶名称</li>
 * </ul>
 * <b>可选字段：</b>
 * <ul>
 *   <li>rootPath - 数据存储根路径（默认 milvus）</li>
 *   <li>region - 区域（AWS 等云平台可能需要）</li>
 * </ul>
 * <p>
 * <b>JSON 示例：</b>
 * <pre>
 * {
 *   "useExternal": true,
 *   "useIAM": false,
 *   "externalEndpoints": "buckettestazure.blob.core.windows.net",
 *   "accessKey": "your-access-key",
 *   "secretKey": "your-secret-key",
 *   "bucketName": "milvus-bucket",
 *   "rootPath": "milvus"
 * }
 * </pre>
 * <p>
 * <h3>方式二：IAM 认证（useIAM=true）</h3>
 * 使用云平台 IAM 角色认证，无需 accessKey/secretKey。
 * <p>
 * <b>必填字段：</b>
 * <ul>
 *   <li>useExternal = true</li>
 *   <li>useIAM = true</li>
 *   <li>externalEndpoints - 存储服务端点（如 s3.us-west-2.amazonaws.com）</li>
 *   <li>bucketName - 桶名称</li>
 * </ul>
 * <b>可选字段：</b>
 * <ul>
 *   <li>rootPath - 数据存储根路径（默认 milvus）</li>
 *   <li>region - 区域（AWS 必填，其他云平台可选）</li>
 *   <li>iamEndpoint - 自定义 IAM 端点（通常不需要设置）</li>
 * </ul>
 * <b>注意：</b>使用 IAM 时，accessKey 和 secretKey 不需要设置。
 * <p>
 * <b>JSON 示例：</b>
 * <pre>
 * {
 *   "useExternal": true,
 *   "useIAM": true,
 *   "externalEndpoints": "s3.us-west-2.amazonaws.com",
 *   "bucketName": "milvus-bucket",
 *   "rootPath": "milvus",
 *   "region": "us-west-2"
 * }
 * </pre>
 * <p>
 * <b>前提条件：</b>Pod 需要绑定对应云平台的 IAM 角色：
 * <ul>
 *   <li>AWS: 配置 IRSA (IAM Roles for Service Accounts)</li>
 *   <li>GCP: 配置 Workload Identity</li>
 *   <li>Azure: 配置 Managed Identity</li>
 *   <li>阿里云: 配置 RRSA</li>
 * </ul>
 */
@Data
public class HelmDependencyConfig {

    // ==================== 通用配置 ====================

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

    // ==================== 外部服务配置（useExternal=true 时使用）====================

    /**
     * 外部服务 Endpoints。
     * <p>
     * <b>AK/SK 和 IAM 两种方式都必填</b>
     * <p>
     * 示例值：
     * <ul>
     *   <li>AWS S3: s3.us-west-2.amazonaws.com</li>
     *   <li>Azure Blob: &lt;account&gt;.blob.core.windows.net</li>
     *   <li>GCP GCS: storage.googleapis.com</li>
     *   <li>阿里云 OSS: oss-cn-hangzhou.aliyuncs.com</li>
     *   <li>MinIO: minio.minio:9000</li>
     *   <li>etcd: etcd-0.etcd:2379,etcd-1.etcd:2379</li>
     *   <li>Pulsar: pulsar-proxy.pulsar:6650</li>
     *   <li>Kafka: kafka-0.kafka:9092,kafka-1.kafka:9092</li>
     * </ul>
     * <p>
     * 前端默认值：""
     */
    String externalEndpoints;

    /**
     * 是否使用 IAM 认证（仅用于外部 S3 存储）。
     * <p>
     * <ul>
     *   <li>true：使用 IAM 认证，不需要设置 accessKey 和 secretKey</li>
     *   <li>false：使用 AK/SK 认证，必须设置 accessKey 和 secretKey</li>
     * </ul>
     * <p>
     * 前端默认值：false
     */
    boolean useIAM;

    /**
     * 访问密钥（Access Key）。
     * <p>
     * <b>AK/SK 方式必填，IAM 方式不需要</b>
     * <p>
     * 前端默认值：""
     */
    String accessKey;

    /**
     * 密钥（Secret Key）。
     * <p>
     * <b>AK/SK 方式必填，IAM 方式不需要</b>
     * <p>
     * 前端默认值：""
     */
    String secretKey;

    /**
     * IAM Endpoint（仅 IAM 方式可选配置）。
     * <p>
     * <b>IAM 方式可选，AK/SK 方式不需要</b>
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
     * <b>AWS 必填，其他云平台可选</b>
     * <p>
     * 示例值：
     * <ul>
     *   <li>AWS: us-west-2, us-east-1, ap-northeast-1</li>
     *   <li>阿里云: cn-hangzhou, cn-beijing, cn-shanghai</li>
     * </ul>
     * <p>
     * 前端默认值：""
     */
    String region;

    /**
     * Bucket 名称（仅用于 S3 存储）。
     * <p>
     * <b>AK/SK 和 IAM 两种方式都必填</b>
     * <p>
     * 前端默认值：milvus-bucket
     */
    String bucketName;

    /**
     * 数据存储根路径（Root Path）。
     * <p>
     * <b>AK/SK 和 IAM 两种方式都可选</b>
     * <p>
     * 用于 etcd/MinIO 的数据隔离，多个 Milvus 实例可共用同一存储但使用不同 rootPath。
     * <p>
     * 前端默认值：milvus
     */
    String rootPath;

    // ==================== 内置服务配置（useExternal=false 时使用）====================

    /**
     * 内置服务的副本数（当 useExternal=false 时）。
     * <p>
     * 前端默认值：1
     */
    int replicaCount;

    /**
     * 存储大小（例如 10Gi）。
     * <p>
     * 前端默认值：10Gi
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
}
