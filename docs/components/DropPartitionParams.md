# DropPartitionParams

删除分区。对应组件：`custom.components.DropPartitionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `partitionName` | String | 是 | | 要删除的分区名称 |

## JSON 示例

```json
{"DropPartitionParams_0": {"partitionName": "partition_a"}}
```
