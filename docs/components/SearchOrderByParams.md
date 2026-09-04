# SearchOrderByParams

向量检索结果按标量字段排序。对应 SDK 3.0.4 的 `SearchReq.orderByFields`，组件为 `custom.components.SearchOrderByComp`。

该组件会从目标 collection 自动抽取 `nq` 条向量作为 query；`collectionName` 为空时使用当前任务最近创建的 collection。

## 参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `collectionName` | String | 否 | collection 名；为空时使用最近创建的 collection |
| `annsField` | String | 是 | 向量字段名 |
| `nq` | int | 是 | query 向量数量，必须大于 0 |
| `topK` | int | 是 | 每个 query 返回的候选数，必须大于 0 |
| `outputFields` | List<String> | 否 | 返回的标量字段；未传为 `[]` |
| `filter` | String | 否 | Milvus filter expression |
| `partitionNames` | List<String> | 否 | 要搜索的分区 |
| `orderByFields` | List | **是** | 排序规则，至少一条 |
| `timeout` | long | 否 | 单次 SDK 请求超时（ms）；未传或 `0` 为 800ms |
| `targetEndpoint` | String | 否 | `primary` / `global` / `secondary` / `secondary_0` 或直接 URI |

`orderByFields` 的条目：

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| `fieldName` | String | 是 | 用于排序的标量字段 |
| `direction` | String | 是 | `ASC` 或 `DESC`（大小写不敏感） |

## JSON 示例

```json
{
  "SearchOrderByParams_0": {
    "collectionName": "products",
    "annsField": "embedding",
    "nq": 1,
    "topK": 10,
    "outputFields": ["price", "category"],
    "filter": "price > 0",
    "partitionNames": [],
    "orderByFields": [
      {"fieldName": "price", "direction": "ASC"},
      {"fieldName": "category", "direction": "DESC"}
    ],
    "timeout": 3000,
    "targetEndpoint": ""
  }
}
```

结果的 `searchResults` 按 SDK 排序规则返回。该功能需要目标 Milvus 服务端支持 SDK 3.0.4 对应的 `orderByFields` 请求字段。
