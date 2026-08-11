# DropCollectionParams

删除 Collection。对应组件：`custom.components.DropCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `dropAll` | boolean | 是 | `false` | 未开启前缀匹配时，true 则删除所有 collection；开启前缀匹配时，true 则删除所有匹配前缀的 collection |
| `collectionName` | String | 否 | `""` | 删除目标 collection 名称；开启前缀匹配时作为前缀使用 |
| `collectionNameUsePrefix` | boolean | 否 | `false` | false 时按完整名称删除；true 时按 `collectionName` 前缀匹配，`dropAll=true` 删除全部匹配项，`dropAll=false` 只删除匹配列表最后一个 |
| `databaseName` | String | 否 | `""` | |

## JSON 示例

只删除匹配前缀列表中的最后一个：

```json
{"DropCollectionParams_0": {"dropAll": false, "collectionName": "Collection_", "collectionNameUsePrefix": true}}
```

删除所有匹配前缀的 collection：

```json
{"DropCollectionParams_0": {"dropAll": true, "collectionName": "Collection_", "collectionNameUsePrefix": true}}
```
