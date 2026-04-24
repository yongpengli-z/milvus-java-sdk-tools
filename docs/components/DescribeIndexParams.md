# DescribeIndexParams

查看索引详情。对应组件：`custom.components.DescribeIndexComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `databaseName` | String | 否 | `""` | |
| `fieldName` | String | 是 | | 要查看索引的字段名 |
| `indexName` | String | 否 | `""` | |

## JSON 示例

```json
{"DescribeIndexParams_0": {"fieldName": "vec"}}
```
