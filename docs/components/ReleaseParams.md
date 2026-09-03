# ReleaseParams

释放 Collection 内存。对应组件：`custom.components.ReleaseCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `releaseAll` | boolean | 是 | `false` | true 则释放所有 collection |
| `collectionName` | String | 否 | `""` | `releaseAll=false` 且未启用多 collection 模式时使用 |
| `collectionNamePrefix` | String | 否 | `""` | collection 名前缀过滤（见下文「多 collection 模式」） |
| `collectionRangeStart` | int | 否 | `-1` | 池区间起始，>=0 启用区间模式 |
| `collectionRangeEnd` | int | 否 | `-1` | 池区间结束（开区间），<=0 表示到末尾 |

## 多 collection 模式

设置 `collectionNamePrefix`（非空）或 `collectionRangeStart`（>=0）时进入多 collection 模式，逐个 release 过滤后的列表：

- 目标集合：`releaseAll=true` 时为实例全量列表（`listCollections`），否则为 `globalCollectionNames` 池子
- 过滤规则与 LoadParams / SearchParams 一致：先按前缀过滤，再按 `[start,end)` 区间过滤（前缀命中 `前缀+纯数字后缀` 时按后缀数值过滤，前导零不影响；否则排序后按下标切片）
- 每个 collection 的 release 结果独立记录在 `releaseResultList`，失败不影响其他 collection

## JSON 示例

```json
{"ReleaseParams_0": {"releaseAll": false, "collectionNamePrefix": "", "collectionRangeStart": -1, "collectionRangeEnd": -1}}
```
