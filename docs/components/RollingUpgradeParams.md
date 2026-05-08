# RollingUpgradeParams

滚动升级实例版本。对应组件：`custom.components.RollingUpgradeComp`

组件会先查询实例类型，再选择升级入口：

- `in01` / `InstanceType=1`：调用 RM `/resource/v1/instance/rolling/upgrade/add/task`
- `in06` / `InstanceType=6`：调用 RM `/resource/v1/vectorlake/{instanceId}/upgrade`
- `in07` / `InstanceType=7`：调用 RM `/resource/v1/vectorlake/query-cluster/{qcInstanceId}/upgrade`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `targetDbVersion` | String | 是 | `""` | 目标版本。`in07` 传空或 `latest-release` 时由 RM 选择最新 QueryCluster 版本；`in06` 需要传精确 dbVersion |
| `forceRestart` | boolean | 是 | `true` | `in01` 表示 forceRestart；`in07` 映射为 force，允许升级 live QN pods |

## JSON 示例

```json
{"RollingUpgradeParams_0": {"targetDbVersion": "v2.5.0", "forceRestart": true}}
```
