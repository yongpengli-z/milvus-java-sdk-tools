# AssertParams

一次性业务断言。对应组件：`custom.components.AssertComp`

该组件用于在流程中集中校验数据正确性。它不会像 `SearchParams` / `QueryParams` 那样按 `runningMinutes` 循环运行；每条 assertion 只执行一次 SDK 请求。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `failFast` | boolean | 否 | `false` | 第一条断言失败后是否停止执行后续 assertion |
| `targetEndpoint` | String | 否 | `""` | 该 AssertParams 下所有 assertion 使用的 endpoint |
| `assertions` | List | 是 | `[]` | 断言列表，前端可动态添加/删除 |

## AssertionItem

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `type` | String | 是 | `query` 或 `search` |
| `metric` | String | 是 | 要断言的指标 |
| `operator` | String | 是 | `eq` / `ne` / `gt` / `gte` / `lt` / `lte` / `between` |
| `expected` | Object | 是 | 预期值；`between` 使用 `[min, max]` |
| `collectionName` | String | 否 | 为空时使用最近一次创建/记录的 collection |
| `collectionRule` | String | 否 | `random` / `sequence` / 空 |
| `filter` | String | 否 | Milvus expr |
| `outputs` | List | 否 | Query/Search output fields；query `count` metric 未配置时自动使用 `["count(*)"]` |
| `partitionNames` | List | 否 | 分区列表 |
| `generalFilterRoleList` | List | 否 | filter 占位符替换规则 |

## QueryAssertion

`type = "query"` 时使用。

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `ids` | List | 否 | 按 ID 查询 |
| `limit` | long | 否 | query limit |
| `offset` | long | 否 | query offset |

## SearchAssertion

`type = "search"` 时使用。

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `annsField` | String | search 必填 | search 向量字段名 |
| `nq` | int | search 可选 | 默认 `1` |
| `topK` | int | search 可选 | 默认 `1` |
| `searchLevel` | int | search 可选 | 默认 `1` |
| `indexAlgo` | String | search 可选 | 写入 searchParams 的 `index_algo` |
| `timeout` | long | search 可选 | SDK 请求超时 ms，默认 `800` |
| `vectorSampleSize` | int | search 可选 | search assertion 从 collection 抽样向量的数量，默认 `max(1000, nq)` |

## 支持的 metric

| type | metric | 说明 |
|------|--------|------|
| `query` | `returnCount` | 一次 query 返回的 entity 数量 |
| `query` | `count` | `count(*)` 返回的真实总量 |
| `search` | `returnCount` | 一次 search 第一个 query vector 返回的结果数 |
| `search` | `totalReturnCount` | `nq > 1` 时所有 query vector 返回结果数总和 |

## 结果语义

- 所有 assertion 通过：`commonResult.result = "success"`
- 任意 assertion 失败：`commonResult.result = "fail"`，调度器会停止后续步骤
- 每条 assertion 结果包含 `actual`、`expected`、`passed`、`message` 和 `details`

## JSON 示例

```json
{
  "AssertParams_0": {
    "failFast": false,
    "targetEndpoint": "",
    "assertions": [
      {
        "type": "query",
        "metric": "count",
        "operator": "eq",
        "expected": 10000,
        "collectionName": "Collection_xxx",
        "collectionRule": "",
        "filter": "id_pk >= 0",
        "outputs": ["count(*)"],
        "partitionNames": [],
        "generalFilterRoleList": [],
        "query": {
          "ids": [],
          "limit": 0,
          "offset": 0
        }
      },
      {
        "type": "search",
        "metric": "returnCount",
        "operator": "eq",
        "expected": 10,
        "collectionName": "Collection_xxx",
        "collectionRule": "",
        "filter": "",
        "outputs": [],
        "partitionNames": [],
        "generalFilterRoleList": [],
        "search": {
          "annsField": "vec",
          "nq": 1,
          "topK": 10,
          "searchLevel": 1,
          "indexAlgo": "",
          "timeout": 800,
          "vectorSampleSize": 1000
        }
      }
    ]
  }
}
```
