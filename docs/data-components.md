##### 5.1.4 Insert：`InsertParams`

对应组件：`custom.components.InsertComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`partitionName`**（string，可空）：前端默认 `""`。
- **`startId`**（long，前端必填）：前端默认 `0`。
- **`numEntries`**（long，前端必填）：总写入量（内部会按 `batchSize` 切批）。前端默认 `1500000`。
- **`batchSize`**（long，前端必填）：前端默认 `1000`。
- **`numConcurrency`**（int，前端必填）：并发线程数。前端默认 `1`。
  - **性能测试建议**：当需要测试 Insert 性能时，推荐添加多个 `InsertParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20），来递增压力，观察不同并发级别下的性能表现。
- **`targetQps`**（int，可选）：目标 QPS（每秒请求数）。前端默认 `0`（不限速）。当值 > 0 时，使用 Guava RateLimiter 控制写入速率。建议先进行性能摸底，再使用固定 QPS。
  - **并发场景注意**：测试 Insert 并发时，如果使用多个 Insert 组件且设置了 `runningMinutes` 作为运行时长，需要将 `numEntries` 设置得足够大，否则数据可能在运行时长结束前就已经插入完毕，导致无法持续压测到预期时长。
  - **避免重复数据**：使用多个 Insert 组件时，应为每个组件设置不同的 `startId`，确保各组件插入的数据 ID 范围不重叠，避免插入重复数据。例如：组件 A 设置 `startId: 0, numEntries: 5000000`，组件 B 设置 `startId: 5000000, numEntries: 5000000`。
- **`fieldDataSourceList`**（list，可空）：字段级数据源配置，指定某个字段从哪个数据集读取数据。未配置的字段默认使用 random 生成。前端默认 `[]`。
  - 每条配置包含 `fieldName`（字段名）和 `dataset`（数据集名称，如 `sift`/`gist`/`deep`/`laion`/`bluesky`/`msmarco-text`）
  - 数据集类型：`sift`/`gist`/`deep`/`laion` 为向量数据集（NPY 格式），`bluesky` 为标量 JSON 数据集（JSON Lines 格式），`msmarco-text` 为纯文本数据集（TXT 格式，用于 VarChar 字段）
  - 示例：`[{"fieldName": "vec", "dataset": "sift"}, {"fieldName": "json_col", "dataset": "bluesky"}, {"fieldName": "text_col", "dataset": "msmarco-text"}]`
- **`runningMinutes`**（long，前端必填）：Insert 中该字段>0 时会成为"时间上限"，否则以数据量批次数为准。前端默认 `0`。
- **`retryAfterDeny`**（boolean，可空）：禁写后是否等待重试。前端默认 `false`。
- **`ignoreError`**（boolean，可空）：出错是否忽略继续。前端默认 `false`。
- **`generalDataRoleList`**（list，可空）：数据生成规则（见 `GeneralDataRole`），仅对未配置 `fieldDataSourceList` 的字段生效。前端默认是"带 1 条空规则"的占位数组；**如果你不使用该能力，建议直接传 `[]`**。

> **注意**：`InsertParams` **没有**顶层 `dataset` 字段。数据集配置**只能通过 `fieldDataSourceList` 指定**，不要在 InsertParams 中添加 `"dataset": "random"` 等多余字段（`dataset` 字段属于 `BulkImportParams`，不是 `InsertParams`）。

**Array of Struct 数据生成说明**：
- Insert/Upsert 组件会自动识别 collection 中的 Array of Struct 字段
- 数据生成时，`genCommonData` 方法会从 `collectionSchema.getStructFields()` 获取 Struct 字段信息
- 每个 Array of Struct 字段会生成随机数量的结构体元素（数量在 1 到 `maxCapacity` 之间）
- Struct 子字段的数据会根据其类型自动生成：
  - 向量字段：生成随机向量
  - 字符串字段：生成随机字符串
  - 数值字段：生成递增或随机数值
- **注意**：Array of Struct 字段不会出现在 `fieldSchemaList` 中，只会出现在 `structFieldSchemaList` 中

##### 5.1.5 Search：`SearchParams`

对应组件：`custom.components.SearchComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`annsField`**（string，前端必填 & 强烈建议显式指定）：向量字段名。前端默认 `vectorField_1`（注意：`createCollectionEdit.vue` 默认向量字段名是 `FloatVector_1`，两者模板不强绑定，实际以你的向量字段名为准）。
  - **Array of Struct 中的向量字段**：如果搜索 Struct 中的向量字段，字段名格式为 `<structFieldName>[<subFieldName>]`（按照 Array 规则使用方括号），例如 `clips[clip_embedding]`。**注意**：该向量字段必须已建索引，否则搜索会失败。
- **`nq`**（int，前端必填）：query vectors 数量。前端默认 `1`。
- **`topK`**（int，前端必填）：前端默认 `1`。
- **`outputs`**（list，建议必填）：输出字段。前端默认 `[]`；不需要输出请传 `[]`。
- **`filter`**（string，可空）：Milvus expr（可包含 `$fieldName` 占位符，见下文）。前端默认 `""`。
- **`numConcurrency`**（int，前端必填）：前端默认 `10`。
  - **性能测试建议**：当需要测试 Search 性能时，推荐添加多个 `SearchParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20、50），来递增压力，观察不同并发级别下的性能表现。
- **`runningMinutes`**（long，前端必填）：Search 是纯按时间循环。前端默认 `10`。
- **`randomVector`**（boolean，前端必填）：前端默认 `true`。
- **`searchLevel`**（int，可空）：前端默认 `1`。
- **`indexAlgo`**（string，可空）：前端默认 `""`。
- **`targetQps`**（double，可空）：前端默认 `0`（0=不限制）。
- **`generalFilterRoleList`**（list，可空）：前端默认是“带 1 条空规则”的占位数组；**如果你不使用该能力，建议直接传 `[]`**。
- **`partitionNames`**（list，可空）：查询分区列表。前端初始模板里是 `""`（占位，失焦后会 split 成数组）；**建议生成 JSON 时直接用 `[]` 或 `["p1","p2"]`**。
- **`ignoreError`**（boolean，可空）：前端默认 `false`。
- **`timeout`**（long，可空）：SDK 请求超时时间（毫秒）。前端默认 `800`。`0` 表示使用默认值 800ms。

##### 5.1.6 Query：`QueryParams`

对应组件：`custom.components.QueryComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`outputs`**（list，建议必填）：前端默认 `[]`。
- **`filter`**（string，可空；支持 `$fieldName` 占位符）：前端默认 `""`。
  - **重要约束**：**`filter` 和 `ids` 必须至少传一个**（不能同时为空）。
  - 如果传了 `ids`（非空数组），可以不传 `filter`（传空字符串 `""`）。
  - 如果传了 `filter`（非空字符串），可以不传 `ids`（传空数组 `[]`）。
  - **推荐写法**：使用主键字段的条件表达式，例如：
    - 主键为 Int64：`"id_pk >= 0"` 或 `"id_pk > 0"`
    - 主键为 VarChar：`"id_pk >= \"0\""` 或 `"id_pk > \"\""`
  - 如果 collection 的主键字段名不是 `id_pk`，请替换为实际的主键字段名。
- **`ids`**（list，建议必填）：前端默认 `[]`（但页面用 `tempIDs` 字符串输入再 split）。
  - **重要约束**：**`filter` 和 `ids` 必须至少传一个**（不能同时为空）。
  - 如果传了 `filter`（非空字符串），可以不传 `ids`（传空数组 `[]`）。
  - 如果传了 `ids`（非空数组），可以不传 `filter`（传空字符串 `""`）。
- **`partitionNames`**（list，可空）：前端初始模板里是 `""`（占位，失焦后会 split 成数组）；**建议生成 JSON 时直接用 `[]` 或 `["p1","p2"]`**。
- **`limit`**（long，可空）：前端初始模板里是 `""`（占位）；**建议生成 JSON 时传数字或不传**。
- **`offset`**（long，前端必填）：前端默认 `0`。
- **`numConcurrency`**（int，前端必填）：前端默认 `10`。
  - **性能测试建议**：当需要测试 Query 性能时，推荐添加多个 `QueryParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20、50），来递增压力，观察不同并发级别下的性能表现。
- **`runningMinutes`**（long，前端必填）：前端默认 `10`。
- **`targetQps`**（double，可空）：前端默认 `0`。
- **`generalFilterRoleList`**（list，可空）：前端默认是“带 1 条空规则”的占位数组；不使用建议传 `[]`。

##### 5.1.7 Upsert：`UpsertParams`

对应组件：`custom.components.UpsertComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`partitionName`**（string，可空）：前端默认 `""`。
- **`startId`**（long，前端必填）：前端默认 `0`。
- **`numEntries`**（long，前端必填）：前端默认 `1500000`。
- **`batchSize`**（long，前端必填）：前端默认 `1000`。
- **`numConcurrency`**（int，前端必填）：前端默认 `1`。
  - **性能测试建议**：当需要测试 Upsert 性能时，推荐添加多个 `UpsertParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20），来递增压力，观察不同并发级别下的性能表现。
- **`fieldDataSourceList`**（list，可空）：字段级数据源配置，与 InsertParams 用法相同。前端默认 `[]`。
  - 示例：`[{"fieldName": "vec", "dataset": "sift"}, {"fieldName": "json_col", "dataset": "bluesky"}, {"fieldName": "text_col", "dataset": "msmarco-text"}]`
- **`runningMinutes`**（long，可空）：>0 时作为时间上限。前端模板里存在该字段且默认 `0`（UI 未展示该输入项）。
- **`retryAfterDeny`**（boolean，可空）：前端默认 `false`。
- **`generalDataRoleList`**（list，可空）：仅对未配置 `fieldDataSourceList` 的字段生效。前端默认是"带 1 条空规则"的占位数组；不使用建议传 `[]`。
- **`targetQps`**（int，可空）：前端默认 `0`。

> **注意**：`UpsertParams` **没有**顶层 `dataset` 字段。数据集配置**只能通过 `fieldDataSourceList` 指定**（同 InsertParams）。

##### 5.1.8 Delete：`DeleteParams`

对应组件：`custom.components.DeleteComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`partitionName`**（string，可空）：前端默认 `""`。
- **`ids`**（list，建议必填）：不按 id 删请传 `[]`。前端默认 `[]`（页面用 `tempIDs` 字符串输入再 split）。
- **`filter`**（string，可空）：前端默认 `""`。

##### 5.1.9 Flush：`FlushParams`

对应组件：`custom.components.FlushComp`

**用途**：Flush 操作用于将 **growing segment** 刷成 **sealed segment**，通常在 Insert 完成后执行，确保数据落盘并触发后续的索引构建。

字段：

- **`flushAll`**（boolean，前端必填）：true 则 flush 实例内所有 collection。前端默认 `false`。
- **`collectionName`**（string，可空）：`flushAll=false` 时使用。前端默认 `""`。

**使用场景**：
- Insert 完成后立即 Flush，确保数据落盘
- 在并发读写测试中，Flush 应该在 Insert 之后、Search/Query 之前执行（或与 Search/Query 并行）
- 如果只是测试写入性能，可以在 Insert 后立即 Flush

