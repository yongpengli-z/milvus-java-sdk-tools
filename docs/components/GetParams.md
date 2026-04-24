# GetParams

按 ID 获取实体（类似 KV get）。对应组件：`custom.components.GetComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `databaseName` | String | 否 | `""` | |
| `ids` | List | 是 | | 要获取的 ID 列表，如 `[1, 2, 3]` 或 `["id_001"]` |
| `outputFields` | List | 否 | `[]` | 输出字段列表 |
| `partitionNames` | List | 否 | `[]` | |

## JSON 示例

```json
{"GetParams_0": {"ids": [1, 2, 3], "outputFields": ["*"], "partitionNames": []}}
```
