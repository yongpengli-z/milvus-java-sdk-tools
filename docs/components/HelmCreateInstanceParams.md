# HelmCreateInstanceParams

通过 Helm Chart 在 Kubernetes 部署 Milvus 实例。对应组件：`custom.components.HelmCreateInstanceComp`

## 参数

### Kubernetes 配置

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `namespace` | String | 是 | `milvus-qtp` | K8s 命名空间 |

### Helm 配置

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `releaseName` | String | 是 | `my-milvus` | Helm Release 名称 |
| `customHelmValues` | List | 否 | `[]` | 自定义 Helm values（转为 `--set`） |
| `milvusConfigItems` | List | 否 | `[]` | Milvus 运行时配置（注入 `user.yaml`） |

`customHelmValues`/`milvusConfigItems` 元素：`{key, value}`

### Milvus 配置

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `milvusMode` | String | 是 | `standalone` | `standalone` 或 `cluster` |
| `milvusImageTag` | String | 否 | | 镜像 Tag，不填用 Chart 默认 |

### 依赖组件（HelmDependencyConfig）

| 字段 | 说明 |
|------|------|
| `etcdConfig` | null 时用 Chart 内置 |
| `minioConfig` | null 时用 Chart 内置 |
| `pulsarConfig` | 仅 Cluster 模式 |
| `kafkaConfig` | 替代 Pulsar |
| `woodpeckerConfig` | Milvus 2.6+ 新流式存储 |

**HelmDependencyConfig 字段**：`useExternal`(bool)、`enabled`(bool)、`externalEndpoints`(string)、`accessKey`/`secretKey`(string)、`bucketName`(string, 默认 `milvus-bucket`)、`rootPath`(string, 默认 `milvus`)、`replicaCount`(int)、`storageSize`(string)、`storageClassName`(string)

### 资源配置（Standalone）

`resources`（HelmResourceConfig，仅 `standalone` 模式）：

| 默认值 | cpuRequest | cpuLimit | memoryRequest | memoryLimit |
|--------|------------|----------|---------------|-------------|
| Standalone | `100m` | `4` | `512Mi` | `8Gi` |

### 组件配置（Cluster）

HelmComponentConfig（仅 `cluster` 模式）：`proxyConfig`/`queryNodeConfig`/`dataNodeConfig`/`indexNodeConfig`/`mixCoordinatorConfig`/`streamingNodeConfig`

字段：`replicas`(int)、`cpuRequest`/`cpuLimit`/`memoryRequest`/`memoryLimit`(string)、`diskSize`(string)

**默认值**：

| 组件 | replicas | cpuReq | cpuLim | memReq | memLim |
|------|----------|--------|--------|--------|--------|
| proxy | 1 | 100m | 2 | 256Mi | 2Gi |
| queryNode | 1 | 500m | 4 | 1Gi | 8Gi |
| dataNode | 1 | 100m | 2 | 256Mi | 2Gi |
| indexNode | 1 | 500m | 4 | 1Gi | 8Gi |
| mixCoordinator | 1 | 100m | 2 | 256Mi | 512Mi |
| streamingNode | 1 | 100m | 2 | 256Mi | 2Gi |

### 部署控制

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `deployArchitecture` | String | `default` | `default`(≤v2.5, 含 indexNode) 或 `streaming`(≥v2.6, 含 streamingNode) |
| `waitTimeoutMinutes` | int | `30` | 等待 Pod Ready 超时 |
| `useHours` | int | `0` | 使用时长，0=不限 |

## Woodpecker 配置（Milvus 2.6+）

替代 Pulsar 的新流式存储。

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | `false` | |
| `storageType` | String | `minio` | `minio`(推荐)/`local`/`service` |
| `imageRepository` | String | `harbor.milvus.io/milvus/woodpecker` | |
| `imageTag` | String | `latest` | |
| `replicas` | int | `4` | 仅 service 模式 |
| `cpuRequest`/`cpuLimit`/`memoryRequest`/`memoryLimit` | String | | 仅 service 模式 |

启用 Woodpecker 自动：禁用 Pulsar、禁用 indexNode、启用 streaming 模式。

## JSON 示例

**Standalone**：

```json
{
  "HelmCreateInstanceParams_0": {
    "namespace": "milvus-qtp", "releaseName": "my-milvus",
    "milvusMode": "standalone", "milvusImageTag": "v2.5.0",
    "waitTimeoutMinutes": 30
  }
}
```

**Cluster**：

```json
{
  "HelmCreateInstanceParams_0": {
    "namespace": "milvus-prod", "releaseName": "milvus-cluster",
    "milvusMode": "cluster", "milvusImageTag": "v2.5.0",
    "deployArchitecture": "default",
    "queryNodeConfig": {"replicas": 3, "cpuRequest": "1", "cpuLimit": "4", "memoryRequest": "2Gi", "memoryLimit": "8Gi"},
    "etcdConfig": {"useExternal": false, "enabled": true, "replicaCount": 3, "storageSize": "10Gi"},
    "waitTimeoutMinutes": 30
  }
}
```

**Woodpecker（minio 模式）**：

```json
{
  "HelmCreateInstanceParams_0": {
    "namespace": "milvus-qtp", "releaseName": "milvus-woodpecker",
    "milvusMode": "cluster", "milvusImageTag": "v2.6.0",
    "woodpeckerConfig": {"enabled": true, "storageType": "minio"},
    "waitTimeoutMinutes": 30
  }
}
```
