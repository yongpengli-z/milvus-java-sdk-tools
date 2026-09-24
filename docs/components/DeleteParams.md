# DeleteParams

删除数据。对应组件：`custom.components.DeleteComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionNamePrefix` | String | 否 | `""` | 非空时进入**多 collection 模式**：对池子中前缀命中的**每个** collection 各执行一次单发删除 |
| `collectionRangeStart` | int | 否 | `-1` | 区间起始（`>=0` 启用多 collection 模式）。数字后缀按后缀数值；否则按名称排序后下标切片 |
| `collectionRangeEnd` | int | 否 | `-1` | 区间结束（开区间，`<=0` 表示到末尾） |
| `partitionName` | String | 否 | `""` | |
| `ids` | List | 建议必填 | `[]` | 按 ID 删除。不按 ID 删请传 `[]` |
| `filter` | String | 否 | `""` | 按 expr 删除 |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## 多 collection 模式

设置 `collectionNamePrefix`（非空）或 `collectionRangeStart`（`>=0`）即进入多 collection 模式：

- 目标集合 = 对 `globalCollectionNames` 池子先按 `collectionNamePrefix` 过滤，再按 `[collectionRangeStart, collectionRangeEnd)` 切分（复用 `CommonFunction.filterCollectionPool`）。
- 过滤规则：前缀命中名称为「前缀+纯数字后缀」时按后缀数值过滤（前导零不影响）；否则按名称排序后取下标切片。`collectionRangeEnd<=0` 表示到末尾。
- **对命中的每个 collection 各执行一次单发删除**（按 `ids`/`filter`，等价 `deleteSingle`），不使用 search-then-delete 持续循环。
- 此模式下 `collectionName`/`runningMinutes`/`deleteNumPerRound` 被忽略。
- `numConcurrency` 语义变为**并发 collection 数**。
- 返回结果为聚合值：`totalCount`/`successCount`/`failCount`，`deletedCount` 为所有 collection 删除总量，`passRate` 为删除成功的 collection 占比。

```json
{
  "DeleteParams_0": {
    "collectionNamePrefix": "new_col_15k_",
    "collectionRangeStart": 0, "collectionRangeEnd": 10000,
    "filter": "id_pk >= 0", "numConcurrency": 10
  }
}
```

## targetEndpoint

用于 Global Cluster 场景选择 Delete 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

## JSON 示例

```json
{"DeleteParams_0": {"ids": [], "filter": "id_pk < 100", "targetEndpoint": ""}}
```
