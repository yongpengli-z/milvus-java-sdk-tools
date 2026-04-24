# QueryParams

标量查询。对应组件：`custom.components.QueryComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | **是** | `""` | `random`/`sequence`/空。**必须显式给值** |
| `outputs` | List | 建议必填 | `[]` | |
| `filter` | String | 条件必填 | `""` | 与 `ids` 至少传一个 |
| `ids` | List | 条件必填 | `[]` | 与 `filter` 至少传一个。**必须显式给 `[]`** |
| `partitionNames` | List | **是** | `[]` | **必须显式给 `[]`** |
| `limit` | long | **是** | `0` | **必须显式给 `0`** |
| `offset` | long | 是 | `0` | **必须显式给 `0`** |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | |
| `targetQps` | double | **是** | `0` | **必须显式给 `0`** |
| `generalFilterRoleList` | List | **是** | `[]` | **必须显式给 `[]`** |

## 约束

- **`filter` 和 `ids` 必须至少传一个**（不能同时为空）。
- 推荐 filter 写法：`"id_pk >= 0"`（Int64 主键）或 `"id_pk >= \"0\""`（VarChar 主键）。

## ⚠️ NPE 必填字段警告

以下字段反序列化后若为 null，框架在 `QueryComp` 统计阶段会抛 `NullPointerException`（报错 `query 统计异常:java.lang.NullPointerException`，`concurrencyNum=0, requestNum=0`）。**生成 JSON 时必须显式给值**：

- `ids: []`
- `partitionNames: []`
- `generalFilterRoleList: []`
- `limit: 0`
- `offset: 0`
- `targetQps: 0`
- `collectionRule: ""`

## 注意事项

- **性能测试建议**：添加多个 QueryParams 组件，设置不同 `numConcurrency` 递增压力。

## JSON 示例（最小可运行）

```json
{
  "QueryParams_0": {
    "collectionName": "xxx",
    "collectionRule": "",
    "outputs": ["id_pk"],
    "filter": "id_pk > 0",
    "ids": [],
    "numConcurrency": 2,
    "runningMinutes": 1,
    "limit": 0,
    "partitionNames": [],
    "offset": 0,
    "generalFilterRoleList": [],
    "targetQps": 0
  }
}
```
