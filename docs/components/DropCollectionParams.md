# DropCollectionParams

删除 Collection。对应组件：`custom.components.DropCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `dropAll` | boolean | 是 | `false` | true 则删除所有 collection |
| `collectionName` | String | 否 | `""` | `dropAll=false` 时使用 |
| `databaseName` | String | 否 | `""` | |

## JSON 示例

```json
{"DropCollectionParams_0": {"dropAll": false}}
```
