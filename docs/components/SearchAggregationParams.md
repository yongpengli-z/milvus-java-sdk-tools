# SearchAggregationParams

在向量检索上执行分桶聚合。对应 SDK 3.0.4 的 `SearchReq.searchAggregation`，组件为 `custom.components.SearchAggregationComp`。

组件会从 collection 自动抽取 `nq` 条向量作为 query。返回结果中的 `aggregationBuckets` 是二维列表：外层与 query 向量一一对应，内层是该 query 的聚合 bucket。

## 基础参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `collectionName` | String | 否 | collection 名；为空时使用最近创建的 collection |
| `annsField` | String | 是 | 向量字段名 |
| `nq` | int | 是 | query 向量数量，必须大于 0 |
| `topK` | int | 是 | 每个 query 返回的候选数，必须大于 0 |
| `outputFields` | List<String> | 否 | top hits / 搜索结果中需要返回的字段 |
| `filter` | String | 否 | Milvus filter expression |
| `partitionNames` | List<String> | 否 | 要搜索的分区 |
| `aggregation` | Object | **是** | 聚合定义 |
| `timeout` | long | 否 | 单次 SDK 请求超时（ms）；未传或 `0` 为 800ms |
| `targetEndpoint` | String | 否 | `primary` / `global` / `secondary` / `secondary_0` 或直接 URI |

## aggregation 定义

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `fields` | List<String> | **是** | 分组字段，至少一项 |
| `size` | long | **是** | 每级最多返回的 bucket 数，必须大于 0 |
| `metrics` | Map | 否 | 指标别名到指标定义的映射 |
| `order` | List | 否 | bucket 排序；`key` 必须是指标别名、`_count` 或 `_key` |
| `topHits` | Object | 否 | 每个 bucket 额外返回的命中记录 |
| `subAggregation` | Object | 否 | 下一级聚合，结构与当前对象相同 |

`metrics` 条目包含 `op`（`AVG`、`SUM`、`COUNT`、`MIN` 或 `MAX`）和 `fieldName`。仅 `COUNT` 可对 `fieldName` 使用 `"*"`。

`order` 条目包含 `key`、`direction`（`ASC` 或 `DESC`）以及可选的 `nullFirst`。

`topHits` 包含正整数 `size` 和可选的 `sort`。每个 `sort` 条目包含 `fieldName`、`direction`（`ASC` / `DESC`）和可选 `nullFirst`。

## JSON 示例

```json
{
  "SearchAggregationParams_0": {
    "collectionName": "products",
    "annsField": "embedding",
    "nq": 1,
    "topK": 100,
    "outputFields": ["price", "brand", "category"],
    "filter": "price > 0",
    "partitionNames": [],
    "aggregation": {
      "fields": ["category"],
      "size": 10,
      "metrics": {
        "avg_price": {"op": "AVG", "fieldName": "price"},
        "item_count": {"op": "COUNT", "fieldName": "*"}
      },
      "order": [
        {"key": "avg_price", "direction": "DESC"},
        {"key": "_key", "direction": "ASC"}
      ],
      "topHits": {
        "size": 3,
        "sort": [{"fieldName": "price", "direction": "DESC"}]
      },
      "subAggregation": {
        "fields": ["brand"],
        "size": 5,
        "order": [{"key": "_count", "direction": "DESC"}]
      }
    },
    "timeout": 3000,
    "targetEndpoint": ""
  }
}
```

服务端必须支持 search aggregation；否则组件会返回 `EXCEPTION` 和服务端错误信息。SDK 在构建请求时会额外校验空分组字段、非正 `size`、非法指标和不合法的排序 key。
