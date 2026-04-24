# RestfulInsertParams

通过 Milvus RESTful 接口 `/v2/vectordb/entities/insert` 写入数据。对应组件：`custom.components.RestfulInsertComp`

## 参数

与 InsertParams 基本一致，区别仅在请求通道（HTTP vs Java SDK）。

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
| `runningMinutes` | long | 是 | `0` | |
| `fieldDataSourceList` | List | 否 | `[]` | |
| `generalDataRoleList` | List | 否 | `[]` | |
| `retryAfterDeny` | boolean | 否 | `false` | |
| `ignoreError` | boolean | 否 | `false` | |
| `lengthFactor` | double | 否 | `0` | |

## 注意事项

- 只有明确要测试 RESTful 写入通道时才使用；常规写入请用 InsertParams（SDK 性能更好）。

## JSON 示例

```json
{
  "RestfulInsertParams_0": {
    "numEntries": 10000, "batchSize": 1000, "numConcurrency": 1,
    "fieldDataSourceList": [], "generalDataRoleList": []
  }
}
```
