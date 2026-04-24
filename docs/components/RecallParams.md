# RecallParams

向量召回率测试。对应组件：`custom.components.RecallComp`

采样向量并记录 base id，以 topK=1 搜索并比较命中率。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `annsField` | String | 建议必填 | | 向量字段名 |
| `searchLevel` | int | 是 | | 搜索精度级别 |

## JSON 示例

```json
{"RecallParams_0": {"annsField": "vec", "searchLevel": 1}}
```
