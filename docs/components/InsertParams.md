# InsertParams

向 Collection 写入数据。对应组件：`custom.components.InsertComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/空 |
| `collectionNamePrefix` | String | 否 | `""` | 非空时进入**多 collection 模式**：对池子中前缀命中的**每个** collection 各写入 `numEntries` 条 |
| `collectionRangeStart` | int | 否 | `-1` | 区间起始（`>=0` 启用多 collection 模式）。数字后缀按后缀数值；否则按名称排序后下标切片 |
| `collectionRangeEnd` | int | 否 | `-1` | 区间结束（开区间，`<=0` 表示到末尾） |
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
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## 多 collection 模式

设置 `collectionNamePrefix`（非空）或 `collectionRangeStart`（`>=0`）即进入多 collection 模式：

- 目标集合 = 对 `globalCollectionNames` 池子先按 `collectionNamePrefix` 过滤，再按 `[collectionRangeStart, collectionRangeEnd)` 切分（复用 `CommonFunction.filterCollectionPool`，与 Search/Load/Release 一致）。
- 过滤规则：前缀命中名称为「前缀+纯数字后缀」时按后缀数值过滤（前导零不影响）；否则按名称排序后取下标切片。`collectionRangeEnd<=0` 表示到末尾。
- **对命中的每个 collection 各写入 `numEntries` 条**（不是总量平均分配）。
- 此模式下 `collectionRule`/`collectionName` 被忽略。
- `numConcurrency` 语义变为**并发 collection 数**（每个 collection 内部按 `batchSize` 串行写）。
- 返回结果为聚合值：`totalCount`/`successCount`/`failCount`，`numEntries` 为所有 collection 写入总量，`rps` 按整体耗时计算。
- 数据集信息（`fieldDataSourceList` 对应的数据集目录遍历与文件行数统计）在整个 Insert 步骤只预加载一次，所有 collection 共用同一套数据集，不会逐 collection 重复检查。

典型用途：`new_col_15k_` 前缀 + 区间切分，将 10w 个 collection 的灌数任务分片到多个 task 并行执行。

```json
{
  "InsertParams_0": {
    "collectionNamePrefix": "new_col_15k_",
    "collectionRangeStart": 0, "collectionRangeEnd": 10000,
    "numEntries": 15000, "batchSize": 1000, "numConcurrency": 10,
    "fieldDataSourceList": [], "generalDataRoleList": []
  }
}
```

## targetEndpoint

用于 Global Cluster 场景选择 Insert 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

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

- **整型 Array 元素为确定性小范围值**：Int8/Int16/Int32/Int64 元素的 Array 字段，行 i 的元素为 `(i+k)%100`（k=元素下标），保证 array_contains 类断言有数据可命中；其他元素类型（VarChar/Float 等）仍为随机生成。

- **InsertParams 没有顶层 `dataset` 字段**。数据集只能通过 `fieldDataSourceList` 指定。
- **性能测试建议**：添加多个 InsertParams 组件，设置不同 `numConcurrency`（1/5/10/20）递增压力。
- **多组件避免重复数据**：为每个组件设置不同 `startId`，确保 ID 范围不重叠。
- **并发压测 + runningMinutes**：需将 `numEntries` 设够大，否则数据提前插完。
- **lengthFactor**：当 `maxLength` 很大（如 65535）时，用 `0.01` 缩小到约 1% 节省带宽。
- **nullableRatio**：仅对 schema 中 `isNullable=true` 的字段生效。覆盖标量、Text/VarChar、Array、向量字段（FloatVector/SparseFloatVector 等稠密/稀疏向量）以及 Array of Struct 中 `isNullable=true` 的子字段。
- **Text / VarChar 长度规则**：
  - `Text`、`VarChar`（以及旧 schema 里的 `String`）会按字段 `maxLength` 生成随机文本。
  - 未配置 `maxLength` 时，默认按 `1024` 处理。
  - `lengthFactor > 0` 时，实际长度会按 `maxLength * lengthFactor` 缩小。
  - 生成逻辑按 UTF-8 字节长度控制，不会超过该字段的 `maxLength`。

## 动态字段数据生成

当 `enableDynamic: true` 时，每行按 `i % 3` 交替 3 种模式（自动处理，无需配置）：
- `i%3==0`：所有 dynamic 字段有值
- `i%3==1`：部分 dynamic 字段值为 null
- `i%3==2`：部分 dynamic 字段缺失

## Array of Struct 数据生成

自动识别 Struct 字段并生成对应数据。Struct 子字段按类型自动生成（向量→随机向量，字符串→随机字符串）。

- Struct 子字段里的 `Text` / `VarChar` 同样按子字段自己的 `maxLength` 生成。
- 如果子字段未配置 `maxLength`，默认按 `1024` 处理。
- 生成结果同样按 UTF-8 字节长度截断，不会超过 `maxLength`。

## JSON 示例

```json
{
  "InsertParams_0": {
    "numEntries": 100000, "batchSize": 1000, "numConcurrency": 5,
    "fieldDataSourceList": [], "generalDataRoleList": [],
    "targetEndpoint": ""
  }
}
```
