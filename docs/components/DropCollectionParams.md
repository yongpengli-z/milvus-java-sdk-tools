# DropCollectionParams

删除 Collection。对应组件：`custom.components.DropCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `dropAll` | boolean | 是 | `false` | true 则删除所有 collection |
| `collectionName` | String | 否 | `""` | `dropAll=false` 时使用 |
| `collectionNameUsePrefix` | boolean | 否 | `false` | false 时按完整名称删除；true 时删除所有以 `collectionName` 开头的 collection |
| `dropPercentage` | double | 否 | `0` | 当 `dropAll=false` 且 `collectionName` 为空时，按比例删除全局记录中的 collection，范围 0-1 |
| `databaseName` | String | 否 | `""` | |

## JSON 示例

```json
{"DropCollectionParams_0": {"dropAll": false, "collectionName": "Collection_", "collectionNameUsePrefix": true}}
```
