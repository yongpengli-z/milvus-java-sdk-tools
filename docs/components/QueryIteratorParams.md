# QueryIteratorParams

迭代式查询，适用于大结果集分页拉取。对应组件：`custom.components.QueryIteratorComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `filter` | String | 否 | `""` | |
| `outputFields` | List | 否 | `[]` | |
| `batchSize` | int | 是 | `100` | 每次拉取的 batch size |
| `limit` | long | 否 | `0` | 最大返回总数，0=不限制 |
| `numConcurrency` | int | 是 | `1` | |
| `runningMinutes` | long | 是 | `1` | |
| `partitionNames` | List | 否 | `[]` | |

## JSON 示例

```json
{
  "QueryIteratorParams_0": {
    "filter": "id_pk >= 0", "outputFields": ["*"],
    "batchSize": 100, "numConcurrency": 1, "runningMinutes": 1, "partitionNames": []
  }
}
```
