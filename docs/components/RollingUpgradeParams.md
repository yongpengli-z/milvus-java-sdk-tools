# RollingUpgradeParams

滚动升级 Milvus 实例版本。对应组件：`custom.components.RollingUpgradeComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `targetDbVersion` | String | 是 | `""` | 目标版本 |
| `forceRestart` | boolean | 是 | `true` | 是否强制重启 |

## JSON 示例

```json
{"RollingUpgradeParams_0": {"targetDbVersion": "v2.5.0", "forceRestart": true}}
```
