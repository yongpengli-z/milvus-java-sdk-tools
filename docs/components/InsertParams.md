# InsertParams

向 Collection 写入数据。对应组件：`custom.components.InsertComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/空 |
| `partitionName` | String | 否 | `""` | |
| `startId` | long | 是 | `0` | 起始 ID |
| `numEntries` | long | 是 | `1500000` | 总写入量 |
| `batchSize` | long | 是 | `1000` | 每批大小 |
| `numConcurrency` | int | 是 | `1` | 并发线程数 |
| `targetQps` | int | 否 | `0` | 目标 QPS，0=不限速 |
| `runningMinutes` | long | 是 | `0` | >0 时作为时间上限，否则以数据量为准 |
| `fieldDataSourceList` | List | 否 | `[]` | 字段级数据源配置（见下文） |
| `generalDataRoleList` | List | 否 | `[]` | 数据生成规则。不使用建议传 `[]` |
| `retryAfterDeny` | boolean | 否 | `false` | 禁写后是否等待重试 |
| `ignoreError` | boolean | 否 | `false` | 出错是否忽略继续 |
| `lengthFactor` | double | 否 | `0` | 随机长度系数 0~1。>0 时长度固定为 `maxLength * lengthFactor` |
| `nullableRatio` | double | 否 | `0.5` | nullable 字段的 null 值比例 0~1 |

## fieldDataSourceList

指定某字段从指定数据集读取数据。未配置的字段默认 random 生成。

每条配置：`{fieldName, dataset}`

可用数据集：
- 向量（NPY）：`sift`/`gist`/`deep`/`laion`
- 标量（JSON Lines）：`bluesky`
- 文本（TXT）：`msmarco-text`
- 标量（Parquet）：`plaud_a_t_dense`

示例：`[{"fieldName": "vec", "dataset": "sift"}, {"fieldName": "text_col", "dataset": "msmarco-text"}]`

## 注意事项

- **InsertParams 没有顶层 `dataset` 字段**。数据集只能通过 `fieldDataSourceList` 指定。
- **性能测试建议**：添加多个 InsertParams 组件，设置不同 `numConcurrency`（1/5/10/20）递增压力。
- **多组件避免重复数据**：为每个组件设置不同 `startId`，确保 ID 范围不重叠。
- **并发压测 + runningMinutes**：需将 `numEntries` 设够大，否则数据提前插完。
- **lengthFactor**：当 `maxLength` 很大（如 65535）时，用 `0.01` 缩小到约 1% 节省带宽。
- **nullableRatio**：仅对 schema 中 `isNullable=true` 的字段生效。

## 动态字段数据生成

当 `enableDynamic: true` 时，每行按 `i % 3` 交替 3 种模式（自动处理，无需配置）：
- `i%3==0`：所有 dynamic 字段有值
- `i%3==1`：部分 dynamic 字段值为 null
- `i%3==2`：部分 dynamic 字段缺失

## Array of Struct 数据生成

自动识别 Struct 字段并生成对应数据。Struct 子字段按类型自动生成（向量→随机向量，字符串→随机字符串）。

## JSON 示例

```json
{
  "InsertParams_0": {
    "numEntries": 100000, "batchSize": 1000, "numConcurrency": 5,
    "fieldDataSourceList": [], "generalDataRoleList": []
  }
}
```
