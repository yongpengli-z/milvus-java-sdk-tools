# RestoreBackupParams

恢复备份。对应组件：`custom.components.RestoreBackupComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `backupId` | String | 否 | | 备份 ID；未填写时可通过预置备份选择自动解析 |
| `backupDataset` | String | 否 | | 预置备份数据集，如 `laion` |
| `backupDim` | int | 否 | `0` | 预置备份维度，如 `768` |
| `backupRowCount` | long | 否 | `0` | 预置备份数据量，如 `1000000` 表示 1m |
| `backupPreset` | String | 否 | | 页面下拉值，如 `laion_768d_1m` / `laion_768d_8m`；后端按当前环境自动解析 backupId |
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

使用预置备份：

```json
{"RestoreBackupParams_0": {"backupPreset": "laion_768d_1m", "restorePolicy": 1}}
```

后端会根据当前任务传入的环境自动选择对应 backupId。当前已配置：

| 下拉值 | 环境 | 数据集 | 维度 | 数据量 | backupId | fromInstanceId |
|--------|------|--------|-----:|-------:|----------|----------------|
| `laion_768d_1m` | `awswest` | `laion` | 768 | 1000000 | `backup11_6a5e6e322049eb5` | `in01-d97eed4bad83877` |
| `laion_768d_8m` | `awswest` | `laion` | 768 | 8000000 | `backup11_9eb2bf7571e5638` | `in01-6ac3a6811b1d9f1` |
| `laion_768d_40m` | `awswest` | `laion` | 768 | 40000000 | `backup11_9668da2c3f00b3a` | `in01-1e65192c6585f6e` |
