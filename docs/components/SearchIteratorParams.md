# SearchIteratorParams

迭代式向量搜索。对应组件：`custom.components.SearchIteratorComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `annsFields` | String | 建议必填 | | 用于从库里 query 出向量样本 |
| `vectorFieldName` | String | 建议必填 | | 传给 SearchIterator 的向量字段名（通常与 `annsFields` 相同） |
| `nq` | int | 是 | | |
| `topK` | int | 是 | | |
| `batchSize` | int | 是 | | iterator 每次拉取的 batch |
| `outputs` | List | 建议必填 | `[]` | |
| `filter` | String | 否 | `""` | 作为 `expr` |
| `metricType` | String | 是 | | 仅识别 `IP`/`COSINE`/其它默认 `L2` |
| `params` | String | 否 | | 如 `"{\"level\": 1}"` |
| `numConcurrency` | int | 是 | | |
| `runningMinutes` | long | 建议必填 | | 建议 >0 |
| `randomVector` | boolean | 是 | | |
| `indexAlgo` | String | 否 | `""` | |
| `useV1` | boolean | 否 | | 保留字段，当前未使用 |

## JSON 示例

```json
{
  "SearchIteratorParams_0": {
    "annsFields": "vec", "vectorFieldName": "vec",
    "nq": 1, "topK": 100, "batchSize": 50, "outputs": ["*"],
    "metricType": "L2", "numConcurrency": 5, "runningMinutes": 1, "randomVector": true
  }
}
```
