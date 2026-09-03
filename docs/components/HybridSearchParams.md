# HybridSearchParams

多向量字段混合搜索（Milvus 2.4+）。对应组件：`custom.components.HybridSearchComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/`sequence_per_request`/空 |
| `collectionNamePrefix` | String | 否 | `""` | collection 名前缀过滤（与 SearchParams 语义一致） |
| `collectionRangeStart` | int | 否 | `-1` | 池区间起始，>=0 启用区间模式（与 SearchParams 语义一致） |
| `collectionRangeEnd` | int | 否 | `-1` | 池区间结束（开区间），<=0 表示到末尾 |
| `searchRequests` | List | 是 | | 搜索请求列表（见下文） |
| `ranker` | String | 是 | `"RRF"` | 融合策略：`RRF` 或 `WeightedRanker` |
| `rankerParams` | Object | 否 | | RRF→`{"k":60}`；WeightedRanker→`{"weights":[0.5,0.5]}` |
| `topK` | int | 是 | `10` | 最终返回数量 |
| `nq` | int | 是 | `1` | |
| `randomVector` | boolean | 是 | `true` | |
| `outputs` | List | 建议必填 | `[]` | |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | 按时间循环 |
| `runningCount` | long | 否 | `0` | 按次数循环：>0 时每线程跑满 N 次后停止（次数优先，不再看时间） |
| `targetQps` | double | 否 | `0` | |
| `generalFilterRoleList` | List | 否 | `[]` | 不使用建议传 `[]` |
| `ignoreError` | boolean | 否 | `false` | |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## Collection 选择

与 SearchParams 一致：`collectionRule` 新增 `sequence_per_request`（每个 hybridSearch 请求轮换取下一个 collection，全局原子游标跨线程唯一，总请求数 ≤ 池子大小时每个 collection 恰好被搜一次）；`collectionNamePrefix` 前缀过滤与 `collectionRangeStart/End` 区间过滤可叠加（先前缀、再区间——前缀命中 `前缀+纯数字后缀` 时按后缀数值过滤，前导零不影响；否则排序后按下标切片）。
`sequence_per_request` 模式下 schema/BM25 Function 检测以池子第一个 collection 为基准，假设池内 collection 同构。

## targetEndpoint

用于 Global Cluster 场景选择 HybridSearch 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

## searchRequests 子结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `annsField` | String | 向量字段名 |
| `topK` | int | 该字段的 topK |
| `searchParams` | Object | 搜索参数 Map，如 `{"level": 1}` |
| `filter` | String | 该字段的 filter（支持 `$fieldName` 占位符） |
| `queryDataset` | String | 该字段的 query 数据集（可选），指定后从数据集文件全量加载查询输入，不从底库捞取；不同字段可配不同数据集（如 dense 字段 `widetable`，BM25 字段 `widetable_bm25`），见 SearchParams.md「Query 数据集」 |

> `metricType` 字段已不再使用，Milvus 根据索引配置自动使用对应 MetricType。

## JSON 示例

```json
{
  "HybridSearchParams_0": {
    "searchRequests": [
      {"annsField": "image_vector", "topK": 10, "searchParams": {"level": 1}, "filter": "", "queryDataset": ""},
      {"annsField": "text_vector", "topK": 10, "searchParams": {"level": 1}, "filter": "", "queryDataset": ""}
    ],
    "ranker": "RRF", "rankerParams": {"k": 60},
    "topK": 10, "nq": 1, "randomVector": true, "outputs": ["*"],
    "numConcurrency": 10, "runningMinutes": 1, "runningCount": 0,
    "collectionRule": "", "collectionNamePrefix": "",
    "collectionRangeStart": -1, "collectionRangeEnd": -1,
    "generalFilterRoleList": [], "ignoreError": true,
    "targetEndpoint": ""
  }
}
```
