# UpdateInstanceComponentParams

批量更新实例的节点组件资源配置。对应组件：`custom.components.UpdateInstanceComponentComp`

更新完成后自动重启实例。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceId` | String | 否 | `""` | 留空使用当前实例 |
| `specs` | List | 是 | | 组件规格列表 |

### specs 元素

| 字段 | 类型 | 说明 |
|------|------|------|
| `category` | String | 节点类型：`queryNode`/`dataNode`/`indexNode`/`proxy` 等 |
| `replicaIndex` | Integer | 副本组索引，留空默认 `1` |
| `replicas` | Integer | 目标 Pod 数，留空不修改 |
| `cpuRequest`/`cpuLimit` | String | K8s CPU 资源，如 `"100m"`/`"2"` |
| `memoryRequest`/`memoryLimit` | String | K8s 内存资源，如 `"512Mi"`/`"2Gi"` |

## 注意事项

- 每个 spec 依次更新 replicas → requests → limits
- 任一步骤失败则 fast-fail
- 更新完成后自动重启并等待 RUNNING

## JSON 示例

```json
{
  "UpdateInstanceComponentParams_0": {
    "specs": [
      {
        "category": "queryNode", "replicas": 2,
        "cpuRequest": "500m", "memoryRequest": "1Gi",
        "cpuLimit": "2", "memoryLimit": "4Gi"
      }
    ]
  }
}
```
