# DeleteParams

删除数据。对应组件：`custom.components.DeleteComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `partitionName` | String | 否 | `""` | |
| `ids` | List | 建议必填 | `[]` | 按 ID 删除。不按 ID 删请传 `[]` |
| `filter` | String | 否 | `""` | 按 expr 删除 |

## JSON 示例

```json
{"DeleteParams_0": {"ids": [], "filter": "id_pk < 100"}}
```
