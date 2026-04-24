# HybridSearchParams

多向量字段混合搜索（Milvus 2.4+）。对应组件：`custom.components.HybridSearchComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/空 |
| `searchRequests` | List | 是 | | 搜索请求列表（见下文） |
| `ranker` | String | 是 | `"RRF"` | 融合策略：`RRF` 或 `WeightedRanker` |
| `rankerParams` | Object | 否 | | RRF→`{"k":60}`；WeightedRanker→`{"weights":[0.5,0.5]}` |
| `topK` | int | 是 | `10` | 最终返回数量 |
| `nq` | int | 是 | `1` | |
| `randomVector` | boolean | 是 | `true` | |
| `outputs` | List | 建议必填 | `[]` | |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | |
| `targetQps` | double | 否 | `0` | |
| `generalFilterRoleList` | List | 否 | `[]` | 不使用建议传 `[]` |
| `ignoreError` | boolean | 否 | `false` | |

## searchRequests 子结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `annsField` | String | 向量字段名 |
| `topK` | int | 该字段的 topK |
| `searchParams` | Object | 搜索参数 Map，如 `{"level": 1}` |
| `filter` | String | 该字段的 filter（支持 `$fieldName` 占位符） |

> `metricType` 字段已不再使用，Milvus 根据索引配置自动使用对应 MetricType。

## JSON 示例

```json
{
  "HybridSearchParams_0": {
    "searchRequests": [
      {"annsField": "image_vector", "topK": 10, "searchParams": {"level": 1}, "filter": ""},
      {"annsField": "text_vector", "topK": 10, "searchParams": {"level": 1}, "filter": ""}
    ],
    "ranker": "RRF", "rankerParams": {"k": 60},
    "topK": 10, "nq": 1, "randomVector": true, "outputs": ["*"],
    "numConcurrency": 10, "runningMinutes": 1,
    "generalFilterRoleList": [], "ignoreError": true
  }
}
```
