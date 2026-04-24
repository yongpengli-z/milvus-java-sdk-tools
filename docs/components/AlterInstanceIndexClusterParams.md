# AlterInstanceIndexClusterParams

切换实例的 Index Cluster。对应组件：`custom.components.AlterInstanceIndexClusterComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceId` | String | 否 | `""` | |
| `indexClusterId` | String | 否 | `""` | 后端是 int |
| `needRestart` | boolean | 是 | `true` | 切换后是否自动重启（轮询等待 RUNNING，最长 30 分钟） |

## JSON 示例

```json
{"AlterInstanceIndexClusterParams_0": {"indexClusterId": "123", "needRestart": true}}
```
