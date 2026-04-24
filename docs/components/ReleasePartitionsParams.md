# ReleasePartitionsParams

释放指定分区的内存。对应组件：`custom.components.ReleasePartitionsComp`

与 ReleaseParams（释放整个 collection）不同，该组件仅释放指定分区。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `partitionNames` | List | 是 | | 要释放的分区名称列表 |

## JSON 示例

```json
{"ReleasePartitionsParams_0": {"partitionNames": ["partition_a"]}}
```
