# HelmDeleteInstanceParams

卸载 Helm 部署的 Milvus 实例。对应组件：`custom.components.HelmDeleteInstanceComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `namespace` | String | 是 | `milvus-qtp` | K8s 命名空间（留空后端默认 `milvus-qtp`） |
| `releaseName` | String | 是 | | 为空尝试从全局 `newInstanceInfo.instanceName` 获取 |
| `deletePvcs` | boolean | 是 | `true` | true=同时删除 PVC（按 release 名匹配，覆盖 minio 等不带 instance 标签的子组件 PVC） |
| `deleteNamespace` | boolean | 是 | `false` | 仅在命名空间为空时才删除 |
| `waitTimeoutMinutes` | int | 是 | `10` | 等待清理超时 |

说明：helm release 不存在时（如 install 失败、release secret 丢失），后端会按 release 名兜底清理残留资源（Deployment/StatefulSet/Service/Pod/Secret/PVC），清到则返回成功。

## JSON 示例

```json
{
  "HelmDeleteInstanceParams_0": {
    "namespace": "milvus-qtp", "releaseName": "my-milvus",
    "deletePvcs": true, "deleteNamespace": false, "waitTimeoutMinutes": 10
  }
}
```
