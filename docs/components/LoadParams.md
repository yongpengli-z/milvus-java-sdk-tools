# LoadParams

加载 Collection 到内存。对应组件：`custom.components.LoadCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `loadAll` | boolean | 是 | `false` | true 则加载实例内所有 collection |
| `collectionName` | String | 否 | `""` | `loadAll=false` 且未启用多 collection 模式时使用 |
| `collectionNamePrefix` | String | 否 | `""` | collection 名前缀过滤（见下文「多 collection 模式」） |
| `collectionRangeStart` | int | 否 | `-1` | 池区间起始下标，>=0 启用区间切片 |
| `collectionRangeEnd` | int | 否 | `-1` | 池区间结束下标（开区间），<=0 表示到末尾 |
| `loadFields` | List | 建议必填 | `[]` | 部分加载字段列表；不使用传 `[]` |
| `skipLoadDynamicField` | boolean | 是 | `false` | 是否跳过加载动态字段 |

## 多 collection 模式

设置 `collectionNamePrefix`（非空）或 `collectionRangeStart`（>=0）时进入多 collection 模式，逐个 load 过滤后的列表：

- 目标集合：`loadAll=true` 时为实例全量列表（`listCollections`），否则为 `globalCollectionNames` 池子
- 过滤规则与 SearchParams 一致：先按前缀过滤，再按名称排序取 `[start,end)` 切片，用于多 client 物理分割（如 client0 取 `[0,334)`、client1 取 `[334,668)`）
- 每个 collection 的 load 结果独立记录在 `loadResultList`，失败不影响其他 collection

## JSON 示例

```json
{"LoadParams_0": {"loadAll": false, "loadFields": [], "skipLoadDynamicField": false, "collectionNamePrefix": "", "collectionRangeStart": -1, "collectionRangeEnd": -1}}
```

按前缀 load 池子中所有 `wt_` 开头的 collection：

```json
{"LoadParams_0": {"loadAll": false, "loadFields": [], "skipLoadDynamicField": false, "collectionNamePrefix": "wt_"}}
```
