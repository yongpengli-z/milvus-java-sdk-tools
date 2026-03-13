##### 5.7.1 Helm 创建实例：`HelmCreateInstanceParams`

对应组件：`custom.components.HelmCreateInstanceComp`

**Kubernetes 配置**：
- **`namespace`**（string）：Kubernetes 命名空间。前端默认：`milvus-qtp`

**Helm 配置**：
- **`releaseName`**（string，必填）：Helm Release 名称（唯一标识）。前端默认：`my-milvus`
- **`customHelmValues`**（list）：自定义 Helm Chart values（Key-Value 列表），用于覆盖 K8s 资源层面的配置。会被转换为 Helm `--set` 参数。
  - 元素类型：`HelmConfigItem`，包含 `key` 和 `value` 字段
  - 常用配置示例：
    - `{"key": "proxy.replicas", "value": "2"}` - 设置 Proxy 副本数
    - `{"key": "service.type", "value": "NodePort"}` - 设置 Service 类型
    - `{"key": "image.repository", "value": "my-registry/milvus"}` - 设置镜像仓库
- **`milvusConfigItems`**（list）：Milvus 运行时配置（Key-Value 列表），用于配置 Milvus 应用层面的参数，会被注入到 `extraConfigFiles.user.yaml`。
  - 元素类型：`HelmConfigItem`，包含 `key` 和 `value` 字段
  - 常用配置示例：
    - `{"key": "common.security.authorizationEnabled", "value": "true"}` - 启用 RBAC
    - `{"key": "log.level", "value": "debug"}` - 设置日志级别
    - `{"key": "proxy.maxTaskNum", "value": "1024"}` - 设置最大并发任务数

**Milvus 配置**：
- **`milvusMode`**（string，必填）：部署模式。可选值：
  - `standalone`：单机模式，适合测试和小规模场景
  - `cluster`：集群模式，适合生产环境
  - 前端默认：`standalone`
- **`milvusImageTag`**（string，可空）：Milvus 镜像版本/Tag（如 `v2.4.0`、`v2.5.0`）。**如果不填写，将直接使用 Helm Chart 中的默认配置**。

**依赖组件配置**（均为 `HelmDependencyConfig` 类型）：
- **`etcdConfig`**：etcd 配置。为 null 时使用 Chart 内置 etcd
- **`minioConfig`**：MinIO 配置。为 null 时使用 Chart 内置 MinIO
- **`pulsarConfig`**：Pulsar 配置（仅 Cluster 模式）。为 null 时使用 Chart 内置 Pulsar
- **`kafkaConfig`**：Kafka 配置（可选，替代 Pulsar）。配置后不会使用 Pulsar
- **`woodpeckerConfig`**（**Milvus 2.6+ 新增**）：Woodpecker 流式存储配置。见下文 `WoodpeckerConfig` 说明

`HelmDependencyConfig` 字段说明：
- **`useExternal`**（boolean）：是否使用外部服务。前端默认：`false`
- **`enabled`**（boolean）：是否启用内置服务（当 `useExternal=false` 时有效）。前端默认：`true`
- **`externalEndpoints`**（string）：外部服务 Endpoints（当 `useExternal=true` 时使用）
  - etcd 示例：`etcd-0.etcd:2379,etcd-1.etcd:2379`
  - MinIO 示例：`minio.minio:9000`
  - Pulsar 示例：`pulsar-proxy.pulsar:6650`
  - Kafka 示例：`kafka-0.kafka:9092,kafka-1.kafka:9092`
- **`accessKey`** / **`secretKey`**（string）：外部服务访问凭证（如 MinIO）
- **`bucketName`**（string）：Bucket 名称（仅 MinIO/S3）。前端默认：`milvus-bucket`
- **`rootPath`**（string）：Root Path（用于 etcd/MinIO 数据隔离）。前端默认：`milvus`
- **`replicaCount`**（int）：内置服务副本数。前端默认：`1`
- **`storageSize`**（string）：存储大小。前端默认：`10Gi`
- **`storageClassName`**（string）：StorageClass 名称（可选）

**资源配置（Standalone 模式）**：
- **`resources`**（`HelmResourceConfig` 类型）：仅在 `milvusMode=standalone` 时生效
  - **`cpuRequest`** / **`cpuLimit`**（string）：CPU 配置，如 `100m`、`2`
  - **`memoryRequest`** / **`memoryLimit`**（string）：内存配置，如 `512Mi`、`2Gi`

**Standalone 模式默认资源配置**：
| 配置项 | 默认值 |
|--------|--------|
| cpuRequest | `100m` |
| cpuLimit | `4` |
| memoryRequest | `512Mi` |
| memoryLimit | `8Gi` |

**Cluster 模式组件配置**（均为 `HelmComponentConfig` 类型，仅 `milvusMode=cluster` 时生效）：
- **`proxyConfig`**：Proxy 组件配置
- **`queryNodeConfig`**：Query Node 组件配置
- **`dataNodeConfig`**：Data Node 组件配置
- **`indexNodeConfig`**：Index Node 组件配置（默认架构 ≤v2.5）
- **`mixCoordinatorConfig`**：Mix Coordinator 组件配置
- **`streamingNodeConfig`**：Streaming Node 组件配置（streaming 架构 ≥v2.6）

`HelmComponentConfig` 字段说明：
- **`replicas`**（int）：副本数。前端默认：`1`
- **`cpuRequest`** / **`cpuLimit`**（string）：CPU 配置
- **`memoryRequest`** / **`memoryLimit`**（string）：内存配置
- **`diskSize`**（string）：磁盘大小（如 queryNode 的磁盘索引）

**Cluster 模式默认资源配置**：
| 组件 | replicas | cpuRequest | cpuLimit | memoryRequest | memoryLimit |
|------|----------|------------|----------|---------------|-------------|
| proxy | 1 | `100m` | `2` | `256Mi` | `2Gi` |
| queryNode | 1 | `500m` | `4` | `1Gi` | `8Gi` |
| dataNode | 1 | `100m` | `2` | `256Mi` | `2Gi` |
| indexNode | 1 | `500m` | `4` | `1Gi` | `8Gi` |
| mixCoordinator | 1 | `100m` | `2` | `256Mi` | `512Mi` |
| streamingNode | 1 | `100m` | `2` | `256Mi` | `2Gi` |

> **注意**：以上为建议的默认配置。如果不填写组件配置，将使用 Helm Chart 中的默认值。

**部署控制**：
- **`deployArchitecture`**（string）：部署架构模式（Cluster 模式）。可选值：
  - `default`：默认架构（≤v2.5），包含 indexNode
  - `streaming`：流式架构（≥v2.6），包含 streamingNode，无 indexNode
  - 前端默认：`default`
- **`waitTimeoutMinutes`**（int）：等待 Pod Ready 的超时时间（分钟）。前端默认：`30`
- **`useHours`**（int）：预计使用时长（小时），用于实例生命周期管理。前端默认：`0`（不限制）

**Woodpecker 配置（Milvus 2.6+ 新增）**：

`WoodpeckerConfig` 是 Milvus 2.6+ 中替代 Pulsar 的新流式存储组件配置。

字段说明：
- **`enabled`**（boolean）：是否启用 Woodpecker。前端默认：`false`
- **`storageType`**（string）：存储类型。可选值：
  - `minio`（**推荐**）：使用 MinIO 对象存储，适用于多节点环境
  - `local`：本地存储，仅适用于单节点或具备共享文件系统的环境
  - `service`：独立服务模式，支持配置副本数和资源
  - 前端默认：`minio`
- **`imageRepository`**（string）：镜像仓库。前端默认：`harbor.milvus.io/milvus/woodpecker`
- **`imageTag`**（string）：镜像 Tag。前端默认：`latest`
- **`replicas`**（int）：副本数（仅 service 模式）。前端默认：`4`
- **`cpuRequest`** / **`cpuLimit`**（string）：CPU 配置（仅 service 模式）
- **`memoryRequest`** / **`memoryLimit`**（string）：内存配置（仅 service 模式）

**注意**：启用 Woodpecker 后会自动：
- 启用 streaming 模式（streaming.enabled=true）
- 禁用 Pulsar（pulsarv3.enabled=false）
- 禁用 indexNode（indexNode.enabled=false）

**Woodpecker 示例 JSON**（minio 模式）：

```json
{
  "HelmCreateInstanceParams_0": {
    "namespace": "milvus-qtp",
    "releaseName": "milvus-woodpecker",
    "milvusMode": "cluster",
    "milvusImageTag": "v2.6.0",
    "woodpeckerConfig": {
      "enabled": true,
      "storageType": "minio"
    },
    "waitTimeoutMinutes": 30
  }
}
```

**Woodpecker service 模式示例**：

```json
{
  "woodpeckerConfig": {
    "enabled": true,
    "storageType": "service",
    "replicas": 4,
    "cpuRequest": "500m",
    "cpuLimit": "2",
    "memoryRequest": "512Mi",
    "memoryLimit": "2Gi"
  }
}
```

**示例 JSON**：

```json
{
  "HelmCreateInstanceParams_0": {
    "namespace": "milvus-qtp",
    "releaseName": "my-milvus",
    "milvusMode": "standalone",
    "milvusImageTag": "v2.5.0",
    "waitTimeoutMinutes": 30
  }
}
```

**Cluster 模式示例**：

```json
{
  "HelmCreateInstanceParams_0": {
    "namespace": "milvus-prod",
    "releaseName": "milvus-cluster",
    "milvusMode": "cluster",
    "milvusImageTag": "v2.5.0",
    "deployArchitecture": "default",
    "proxyConfig": {
      "replicas": 2,
      "cpuRequest": "500m",
      "cpuLimit": "2",
      "memoryRequest": "1Gi",
      "memoryLimit": "4Gi"
    },
    "queryNodeConfig": {
      "replicas": 3,
      "cpuRequest": "1",
      "cpuLimit": "4",
      "memoryRequest": "2Gi",
      "memoryLimit": "8Gi"
    },
    "dataNodeConfig": {
      "replicas": 2
    },
    "indexNodeConfig": {
      "replicas": 1
    },
    "etcdConfig": {
      "useExternal": false,
      "enabled": true,
      "replicaCount": 3,
      "storageSize": "10Gi"
    },
    "minioConfig": {
      "useExternal": false,
      "enabled": true,
      "replicaCount": 1,
      "storageSize": "50Gi"
    },
    "milvusConfigItems": [
      {"key": "log.level", "value": "info"},
      {"key": "common.security.authorizationEnabled", "value": "true"}
    ],
    "waitTimeoutMinutes": 30
  }
}
```

##### 5.7.2 Helm 删除实例：`HelmDeleteInstanceParams`

对应组件：`custom.components.HelmDeleteInstanceComp`

- **`namespace`**（string）：Kubernetes 命名空间。前端默认：`milvus-qtp`
- **`releaseName`**（string，必填）：Helm Release 名称。如果为空，会尝试从全局 `newInstanceInfo.instanceName` 获取
- **`deletePvcs`**（boolean）：是否删除 PVC（持久化存储卷）。
  - `true`：同时删除关联的 PVC，彻底清理数据
  - `false`：保留 PVC，数据可恢复
  - 前端默认：`false`
- **`deleteNamespace`**（boolean）：是否删除命名空间（仅在命名空间为空时才会删除）。前端默认：`false`
- **`waitTimeoutMinutes`**（int）：等待资源清理完成的超时时间（分钟）。前端默认：`10`

**示例 JSON**：

```json
{
  "HelmDeleteInstanceParams_0": {
    "namespace": "milvus-qtp",
    "releaseName": "my-milvus",
    "deletePvcs": true,
    "deleteNamespace": false,
    "waitTimeoutMinutes": 10
  }
}
```

**完整流程示例（创建实例 → 执行测试 → 删除实例）**：

```json
{
  "HelmCreateInstanceParams_0": {
    "namespace": "milvus-qtp",
    "releaseName": "test-milvus",
    "milvusMode": "standalone",
    "milvusImageTag": "v2.5.0",
    "waitTimeoutMinutes": 30
  },
  "CreateCollectionParams_1": {
    "collectionName": "test_collection",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id",
        "primaryKey": true,
        "autoId": true
      },
      {
        "dataType": "FloatVector",
        "fieldName": "vector",
        "dim": 128
      }
    ]
  },
  "HelmDeleteInstanceParams_2": {
    "releaseName": "test-milvus",
    "deletePvcs": true,
    "waitTimeoutMinutes": 10
  }
}
```

---

