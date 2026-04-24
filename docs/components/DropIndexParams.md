# DropIndexParams

删除索引。对应组件：`custom.components.DropIndexComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `fieldName` | String | 是 | `""` | 要 drop 的索引字段名 |

## JSON 示例

```json
{"DropIndexParams_0": {"fieldName": "vec"}}
```
