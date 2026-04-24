# HelmDeleteInstanceParams

卸载 Helm 部署的 Milvus 实例。对应组件：`custom.components.HelmDeleteInstanceComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `namespace` | String | 是 | `milvus-qtp` | K8s 命名空间 |
| `releaseName` | String | 是 | | 为空尝试从全局 `newInstanceInfo.instanceName` 获取 |
| `deletePvcs` | boolean | 是 | `false` | true=同时删除 PVC（彻底清理数据） |
| `deleteNamespace` | boolean | 是 | `false` | 仅在命名空间为空时才删除 |
| `waitTimeoutMinutes` | int | 是 | `10` | 等待清理超时 |

## JSON 示例

```json
{
  "HelmDeleteInstanceParams_0": {
    "namespace": "milvus-qtp", "releaseName": "my-milvus",
    "deletePvcs": true, "deleteNamespace": false, "waitTimeoutMinutes": 10
  }
}
```
