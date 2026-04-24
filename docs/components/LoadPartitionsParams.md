# LoadPartitionsParams

加载指定分区到内存。对应组件：`custom.components.LoadPartitionsComp`

与 LoadParams（加载整个 collection）不同，该组件仅加载指定分区。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `partitionNames` | List | 是 | | 要加载的分区名称列表 |

## JSON 示例

```json
{"LoadPartitionsParams_0": {"partitionNames": ["partition_a", "partition_b"]}}
```
