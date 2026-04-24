# UpsertParams

更新/插入数据。对应组件：`custom.components.UpsertComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/空 |
| `partitionName` | String | 否 | `""` | |
| `startId` | long | 是 | `0` | |
| `numEntries` | long | 是 | `1500000` | |
| `batchSize` | long | 是 | `1000` | |
| `numConcurrency` | int | 是 | `1` | |
| `targetQps` | int | 否 | `0` | |
| `runningMinutes` | long | 否 | `0` | |
| `fieldDataSourceList` | List | 否 | `[]` | 字段级数据源，与 InsertParams 用法相同 |
| `generalDataRoleList` | List | 否 | `[]` | 不使用建议传 `[]` |
| `retryAfterDeny` | boolean | 否 | `false` | |
| `lengthFactor` | double | 否 | `0` | 与 InsertParams 语义一致 |
| `nullableRatio` | double | 否 | `0.5` | |
| `partialUpdate` | boolean | 否 | `false` | 是否启用部分更新 |
| `updateFieldNames` | List | 否 | `[]` | 部分更新的字段名列表（仅 `partialUpdate=true` 时生效） |

## 注意事项

- **没有顶层 `dataset` 字段**，数据集只能通过 `fieldDataSourceList` 指定。
- **autoID 场景**：即使主键 `autoId: true`，Upsert 数据中**也必须包含主键值**。框架自动生成主键数据。
- **部分更新**：`partialUpdate: true` 时，仅更新 `updateFieldNames` 中指定的字段，其余保持不变。`updateFieldNames` 不需要包含主键字段。
- **性能测试建议**：添加多个组件，设置不同 `numConcurrency` 递增压力。

## JSON 示例

```json
{
  "UpsertParams_0": {
    "numEntries": 10000, "batchSize": 1000, "numConcurrency": 1,
    "fieldDataSourceList": [], "generalDataRoleList": []
  }
}
```

**部分更新示例**：

```json
{
  "UpsertParams_0": {
    "numEntries": 10000, "batchSize": 1000, "numConcurrency": 1,
    "partialUpdate": true,
    "updateFieldNames": [{"fieldName": "varchar_col"}, {"fieldName": "int_col"}],
    "fieldDataSourceList": [], "generalDataRoleList": []
  }
}
```
