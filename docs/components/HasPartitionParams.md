# HasPartitionParams

检查分区是否存在。对应组件：`custom.components.HasPartitionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `partitionName` | String | 是 | | 要检查的分区名称 |

## JSON 示例

```json
{"HasPartitionParams_0": {"partitionName": "partition_a"}}
```
