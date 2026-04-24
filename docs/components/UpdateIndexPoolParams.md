# UpdateIndexPoolParams

更新 Index Pool 镜像版本。对应组件：`custom.components.UpdateIndexPoolComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `managerImageTag` | String | 否 | `""` | Manager 镜像 Tag |
| `workerImageTag` | String | 否 | `""` | Worker 镜像 Tag |
| `indexClusterId` | String | 否 | `""` | Index Cluster ID（后端是 int） |

## JSON 示例

```json
{"UpdateIndexPoolParams_0": {"managerImageTag": "v1.0", "workerImageTag": "v1.0"}}
```
