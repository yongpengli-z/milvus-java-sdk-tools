# AlterInstanceIndexClusterParams

切换实例的 Index Cluster。对应组件：`custom.components.AlterInstanceIndexClusterComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceId` | String | 否 | `""` | |
| `indexClusterId` | String | 否 | `""` | 后端是 int |
| `needRestart` | boolean | 是 | `true` | 切换后是否自动重启；自动重启走 CloudOps admin 路径，并轮询等待 RUNNING，最长 30 分钟 |
| `accountEmail` | String | 否 | `""` | 可选；非空时强制用该 Cloud 账号刷新当前用户上下文 |
| `accountPassword` | String | 否 | `""` | 可选；配合 `accountEmail` 使用 |

## 注意事项

- `needRestart=true` 时不需要重复填写创建实例使用的账号密码；组件会使用 CloudOps admin 接口发起重启，避免用户态 RM restart 因 index cluster 权限不足失败。
- `accountEmail/accountPassword` 仅用于显式切换当前 Cloud 用户上下文，不是重启权限的来源。

## JSON 示例

```json
{"AlterInstanceIndexClusterParams_0": {"indexClusterId": "123", "needRestart": true}}
```
