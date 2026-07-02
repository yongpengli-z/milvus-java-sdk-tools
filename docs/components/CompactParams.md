# CompactParams

Compact（压缩合并 segment）。对应组件：`custom.components.CompactComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `compactAll` | boolean | 是 | `false` | true 则 compact 所有 collection |
| `collectionName` | String | 否 | `""` | `compactAll=false` 时使用 |
| `clustering` | boolean | 是 | `false` | 是否 clustering compaction（JSON key 用 `clustering` 或 `isClustering`） |
| `targetSize` | Long | 否 | | Compaction 目标 segment 大小，单位 MB；不传或小于等于 0 时使用 Milvus 默认值 |

## JSON 示例

```json
{"CompactParams_0": {"compactAll": false, "clustering": false}}
```

带 `targetSize`：

```json
{"CompactParams_0": {"compactAll": false, "clustering": false, "targetSize": 1024}}
```
