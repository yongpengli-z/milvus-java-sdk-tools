# CreatePartitionParams

创建分区。对应组件：`custom.components.CreatePartitionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `partitionName` | String | 是 | | 分区名称 |

## 注意事项

- 手动创建分区与 `numPartitions`（Partition Key）是两种不同的分区机制，互不影响。

## JSON 示例

```json
{"CreatePartitionParams_0": {"partitionName": "partition_a"}}
```
