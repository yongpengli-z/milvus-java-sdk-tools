package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * Helm 方式创建 Milvus 实例参数。
 * <p>
 * 用于在任意 Kubernetes 环境中通过 Helm Chart 部署 Milvus 实例。
 * <p>
 * 支持 Standalone 和 Cluster 两种部署模式，支持内置依赖组件和外部依赖组件。
 */
@Data
public class HelmCreateInstanceParams {

    // ==================== Kubernetes 配置 ====================
    // 注意：kubeconfig 路径由 EnvEnum 内置控制，不需要在参数中指定
    // 注意：命名空间已预先创建好，默认使用 qa

    /**
     * Kubernetes 命名空间。
     * <p>
     * 前端默认值：`qa`
     */
    String namespace;

    // ==================== Helm 配置 ====================
    // 使用远程仓库：https://zilliztech.github.io/milvus-helm

    /**
     * Helm Release 名称（唯一标识）。
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：`my-milvus`
     */
    String releaseName;

    /**
     * 自定义 Helm Chart values（Key-Value 列表）。
     * <p>
     * 用于覆盖 Helm Chart 的部署配置（K8s 资源层面）。
     * <p>
     * 会被转换为 Helm --set 参数。
     * <p>
     * 常用配置示例：
     * - key: `proxy.replicas`, value: `2` - 设置 Proxy 副本数
     * - key: `service.type`, value: `NodePort` - 设置 Service 类型
     * - key: `image.repository`, value: `my-registry/milvus` - 设置镜像仓库
     * - key: `standalone.resources.limits.memory`, value: `8Gi` - 设置内存限制
     * <p>
     * JSON 示例：
     * <pre>
     * [
     *   {"key": "proxy.replicas", "value": "2"},
     *   {"key": "service.type", "value": "NodePort"}
     * ]
     * </pre>
     * <p>
     * 前端默认值：null 或空列表
     */
    List<HelmConfigItem> customHelmValues;

    /**
     * Milvus 运行时配置（Key-Value 列表）。
     * <p>
     * 用于配置 Milvus 应用层面的参数，会被注入到 extraConfigFiles.user.yaml。
     * <p>
     * 常用配置示例：
     * - key: `common.security.authorizationEnabled`, value: `true` - 启用 RBAC
     * - key: `log.level`, value: `debug` - 设置日志级别
     * - key: `proxy.maxTaskNum`, value: `1024` - 设置最大并发任务数
     * - key: `quotaAndLimits.dml.insertRate.max`, value: `-1` - 取消插入速率限制
     * <p>
     * JSON 示例：
     * <pre>
     * [
     *   {"key": "log.level", "value": "debug"},
     *   {"key": "common.security.authorizationEnabled", "value": "true"}
     * ]
     * </pre>
     * <p>
     * 前端默认值：null 或空列表
     */
    List<HelmConfigItem> milvusConfigItems;

    // ==================== Milvus 配置 ====================

    /**
     * Milvus 部署模式。
     * <p>
     * 可选值：
     * - `standalone`：单机模式，适合测试和小规模场景
     * - `cluster`：集群模式，适合生产环境
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：`standalone`
     */
    String milvusMode;

    /**
     * Milvus 镜像版本/Tag。
     * <p>
     * 示例值：`v2.4.0`、`v2.3.5`、`v2.5.0`
     * <p>
     * 如果为空，使用 Chart 默认版本。
     * <p>
     * 前端默认值：""（空字符串，使用 Chart 默认）
     */
    String milvusImageTag;

    // ==================== 依赖组件配置 ====================

    /**
     * etcd 配置。
     * <p>
     * 如果为 null，使用 Chart 内置 etcd（默认配置）。
     * <p>
     * Standalone 模式默认 1 副本，Cluster 模式默认 3 副本。
     */
    HelmDependencyConfig etcdConfig;

    /**
     * MinIO 配置。
     * <p>
     * 如果为 null，使用 Chart 内置 MinIO（默认配置）。
     */
    HelmDependencyConfig minioConfig;

    /**
     * Pulsar 配置（仅 Cluster 模式需要）。
     * <p>
     * 如果为 null 且为 Cluster 模式，使用 Chart 内置 Pulsar。
     * <p>
     * Standalone 模式下会自动禁用 Pulsar。
     */
    HelmDependencyConfig pulsarConfig;

    /**
     * Kafka 配置（可选，替代 Pulsar）。
     * <p>
     * 如果配置了 Kafka，则不会使用 Pulsar。
     * <p>
     * 仅在 Cluster 模式下有效。
     */
    HelmDependencyConfig kafkaConfig;

    // ==================== 资源配置（Standalone 模式） ====================

    /**
     * Standalone 模式下的资源配置。
     * <p>
     * 仅在 milvusMode 为 `standalone` 时生效。
     * <p>
     * 可选配置，为 null 则使用 Chart 默认值。
     */
    HelmResourceConfig resources;

    // ==================== Cluster 模式组件配置 ====================
    // 以下配置仅在 milvusMode 为 `cluster` 时生效

    /**
     * Proxy 组件配置（Cluster 模式）。
     * <p>
     * Proxy 负责接收客户端请求并转发到其他组件。
     * <p>
     * 前端默认值：null（使用 Chart 默认配置）
     */
    HelmComponentConfig proxyConfig;

    /**
     * Query Node 组件配置（Cluster 模式）。
     * <p>
     * Query Node 负责执行查询操作。
     * <p>
     * 前端默认值：null（使用 Chart 默认配置）
     */
    HelmComponentConfig queryNodeConfig;

    /**
     * Data Node 组件配置（Cluster 模式）。
     * <p>
     * Data Node 负责数据写入和持久化。
     * <p>
     * 前端默认值：null（使用 Chart 默认配置）
     */
    HelmComponentConfig dataNodeConfig;

    /**
     * Index Node 组件配置（Cluster 模式）。
     * <p>
     * Index Node 负责构建索引。
     * <p>
     * 前端默认值：null（使用 Chart 默认配置）
     */
    HelmComponentConfig indexNodeConfig;

    /**
     * Mix Coordinator 组件配置（Cluster 模式）。
     * <p>
     * Mix Coordinator 整合了 Root/Query/Index/Data Coordinator 的功能。
     * <p>
     * 前端默认值：null（使用 Chart 默认配置）
     */
    HelmComponentConfig mixCoordinatorConfig;

    /**
     * Streaming Node 组件配置（Cluster 模式，Milvus 2.6+ streaming 架构）。
     * <p>
     * Streaming Node 负责 WAL（Write-Ahead Log）管理。
     * <p>
     * 仅在 deployArchitecture 为 "streaming" 时有效。
     * <p>
     * 前端默认值：null（使用 Chart 默认配置）
     */
    HelmComponentConfig streamingNodeConfig;

    // ==================== 部署控制 ====================

    /**
     * 部署架构模式（Cluster 模式）。
     * <p>
     * 可选值：
     * - `default`：默认架构（≤v2.5），包含 indexNode
     * - `streaming`：流式架构（≥v2.6），包含 streamingNode，无 indexNode
     * <p>
     * 前端默认值：`default`
     */
    String deployArchitecture;

    /**
     * 等待 Pod Ready 的超时时间（分钟）。
     * <p>
     * 前端默认值：30
     */
    int waitTimeoutMinutes;

    /**
     * 预计使用时长（小时），用于实例生命周期管理。
     * <p>
     * 设置后，后端会计算预计到期时间，用于后续自动销毁实例。
     * 前端默认值：0（不限制）
     */
    int useHours;
}
