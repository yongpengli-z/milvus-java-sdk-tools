# AssertParams

一次性业务断言。对应组件：`custom.components.AssertComp`

该组件用于在流程中集中校验数据正确性。它不会像 `SearchParams` / `QueryParams` 那样按 `runningMinutes` 循环运行；每条 assertion 只执行一次 SDK 请求。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `failFast` | boolean | 否 | `false` | 第一条断言失败后是否停止执行后续 assertion |
| `targetEndpoint` | String | 否 | `""` | 该 AssertParams 下所有 assertion 使用的 endpoint |
| `collectionName` | String | 否 | `""` | 该 AssertParams 下所有 assertion 使用的 collection；为空时使用最近一次创建/记录的 collection |
| `collectionRule` | String | 否 | `""` | `random` / `sequence` / 空 |
| `assertions` | List | 是 | `[]` | 断言列表，前端可动态添加/删除 |

## AssertionItem

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `type` | String | 是 | `query` / `search` / `describeIndex` |
| `metric` | String | 是 | 要断言的指标 |
| `operator` | String | 是 | `eq` / `ne` / `gt` / `gte` / `lt` / `lte` / `between` |
| `expected` | Object | 是 | 预期值；`between` 使用 `[min, max]` |
| `filter` | String | 否 | Milvus expr |
| `outputs` | List | 否 | Query/Search output fields；query `count` metric 未配置时自动使用 `["count(*)"]` |
| `partitionNames` | List | 否 | 分区列表 |
| `generalFilterRoleList` | List | 否 | filter 占位符替换规则 |

## QueryAssertion

`type = "query"` 时使用。

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `ids` | List | 否 | 按 ID 查询 |
| `limit` | long | 否 | query limit；`idSetEquals` 未配置时默认 16384 |
| `offset` | long | 否 | query offset |
| `compareFilter` | String | idSetEquals 必填 | 与 `filter` 语义等价的第二个表达式，两次 query 的主键集合做一致性比对 |

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
| `compareParams` | Object | search idSetEquals 必填 | 叠加在基础 searchParams 上的第二组参数（如 `{"offset":5}`），同一批向量各搜一次比对 ID 集合 |
| `hybrid` | boolean | search 可选 | 默认 `false`。idSetEquals 时 true = 用 hybridSearch（单个 AnnSearchReq）执行比对，用于验证 hybrid_search 特有问题（如 offset 是否被忽略） |

## DescribeIndexAssertion

`type = "describeIndex"` 时使用。

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `fieldName` | String | 否 | 要 describe 的 field；`fieldName` 和 `indexName` 至少传一个 |
| `indexName` | String | 否 | 指定 index 名称。适合同一 field 存在多个 index 的场景，例如 JSON path index |
| `databaseName` | String | 否 | Database 名称 |

## 支持的 metric

| type | metric | 说明 |
|------|--------|------|
| `query` | `returnCount` | 一次 query 返回的 entity 数量 |
| `query` | `count` | `count(*)` 返回的真实总量 |
| `query` | `idSetEquals` | 用 `filter` 和 `query.compareFilter` 各查一次，比对返回的主键集合是否一致（actual=Boolean，配 `operator=eq, expected=true`）。用于验证谓词合并/改写类变更不改变查询结果。**VACUOUS 防护：两集合均为空（两 filter 都查出 0 行）时判失败**——空集==空集无校验意义，需检查数据生成或过滤条件 |
| `search` | `returnCount` | 一次 search 第一个 query vector 返回的结果数 |
| `search` | `totalReturnCount` | `nq > 1` 时所有 query vector 返回结果数总和 |
| `search` | `idSetEquals` | 同一批向量，用基础参数和叠加 `search.compareParams` 后的参数各搜一次，比对首个 query vector 返回的 ID 集合（actual=Boolean）。验证 offset 等参数是否生效：`operator=ne, expected=false` 表示两次结果必须不同。同样带 VACUOUS 防护（两集合均空判失败） |
| `describeIndex` | `indexedRows` | 指定 index 已完成索引的行数 |
| `describeIndex` | `totalRows` | 指定 index 需要索引的总行数 |
| `describeIndex` | `pendingIndexRows` | 指定 index 待索引的行数 |

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
    "collectionName": "Collection_xxx",
    "collectionRule": "",
    "assertions": [
      {
        "type": "query",
        "metric": "count",
        "operator": "eq",
        "expected": 10000,
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
        "type": "query",
        "metric": "idSetEquals",
        "operator": "eq",
        "expected": true,
        "filter": "array_contains(arr, 1) or array_contains(arr, 2) or array_contains(arr, 3)",
        "outputs": [],
        "partitionNames": [],
        "generalFilterRoleList": [],
        "query": {
          "ids": [],
          "limit": 0,
          "offset": 0,
          "compareFilter": "array_contains_any(arr, [1, 2, 3])"
        }
      },
      {
        "type": "search",
        "metric": "returnCount",
        "operator": "eq",
        "expected": 10,
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
      },
      {
        "type": "describeIndex",
        "metric": "pendingIndexRows",
        "operator": "eq",
        "expected": 0,
        "describeIndex": {
          "fieldName": "dense_float_vec",
          "indexName": "",
          "databaseName": ""
        }
      }
    ]
  }
}
```
