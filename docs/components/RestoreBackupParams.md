# RestoreBackupParams

恢复备份。对应组件：`custom.components.RestoreBackupComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `backupId` | String | 是 | | 备份 ID |
| `restorePolicy` | int | 是 | `1` | 恢复策略 |
| `notChangeStatus` | boolean | 否 | `false` | |
| `skipCreateCollection` | boolean | 否 | `false` | |
| `toInstanceId` | String | 否 | `""` | 目标实例 ID |
| `truncateBinlogByTs` | boolean | 否 | `false` | |
| `withRBAC` | boolean | 否 | `false` | |

## JSON 示例

```json
{"RestoreBackupParams_0": {"backupId": "backup_123", "restorePolicy": 1}}
```
