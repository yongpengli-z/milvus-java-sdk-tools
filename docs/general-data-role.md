### 7. 数据/过滤生成规则：`GeneralDataRole`（高级但很有用）

`GeneralDataRole`（`custom.pojo.GeneralDataRole`）可用于两类场景：

- **Insert/Upsert 的数据生成**：通过 `generalDataRoleList` 控制某些字段的取值分布/序列。
- **Search/Query 的 filter 参数替换**：通过 `generalFilterRoleList` 动态生成 filter 中的 `$占位符`。

结构：

- **`fieldName`**：字段名
- **`prefix`**：字符串前缀（用于 VarChar/String）
- **`sequenceOrRandom`**：`sequence` 或其它（其它视为 random）
- **`randomRangeParamsList`**：范围与占比（见下）

`RandomRangeParams`（`custom.pojo.RandomRangeParams`）：

- **`start`** / **`end`**：范围
- **`rate`**：概率（0~1）。`advanceRandom()` 会按概率桶选择范围再取随机值。

filter 占位符规则（Search/Query）：

- 在 `filter` 字符串里写 `$<fieldName>`，例如：`"id_pk > $id_pk"`
- 执行时会把 `$id_pk` 替换成 `prefix + 数值`（数值按 random/sequence 规则生成）

> 说明：`sequence` 的 filter 生成目前只支持 `randomRangeParamsList` 的第一条规则（见 `advanceSequenceForSearch`）。

---

### 8. 推荐的 LLM 输出格式（给 LLM 的硬约束）

如果你希望 LLM 根据自然语言生成 `customize_params`，建议把下面规则直接作为 Prompt 的“输出规范”：

- **只输出一个 JSON object**（不要输出解释文字）。
- **简洁输出（节省输出 token）**：
  - **不要输出任何解释、注释、说明文字**，只输出纯 JSON。
  - **JSON 使用紧凑格式**：去掉不必要的换行和缩进，尽量压缩输出长度。如果 JSON 较短（< 2000 字符），可以使用单行或少量换行；如果 JSON 较长，允许适度换行以保证可读性，但不要每个字段都换行。
  - **省略与默认值相同的非必要字段**：以下字段如果值等于默认值，可以省略不写（Java 反序列化会自动取默认值）：
    - `collectionName:""`、`databaseName:""`、`partitionName:""`、`collectionRule:""`、`filter:""`、`indexAlgo:""` 等空字符串字段
    - `startId:0`、`runningMinutes:0`、`targetQps:0`、`offset:0`、`searchLevel:1` 等零值/默认数字字段
    - `retryAfterDeny:false`、`ignoreError:false`、`flushAll:false`、`releaseAll:false`、`dropAll:false`、`compactAll:false`、`skipLoadDynamicField:false` 等 false 值字段
    - `functionParams:null`、`properties:[]` 等空值字段
  - **不可省略的字段**（即使是默认值也必须显式写出）：
    - `fieldParamsList` 中的 boolean 字段：`autoId`、`partitionKey`、`nullable`、`enableMatch`、`enableAnalyzer`（避免反序列化歧义）
    - `fieldParamsList` 中的 `analyzerParamsList:[]`（避免 NPE）
    - 所有 List 类型字段：`loadFields:[]`、`outputs:[]`、`ids:[]`、`generalDataRoleList:[]`、`generalFilterRoleList:[]`、`indexParams:[]` 等（避免 NPE）
    - `numPartitions`（与 partitionKey 有约束关系）
    - `shardNum`、`enableDynamic`（CreateCollection 核心字段）
    - `numEntries`、`batchSize`、`numConcurrency`（Insert/Search/Query 核心字段）
    - `fieldDataSourceList`（Insert/Upsert，不使用时传 `[]`）
    - **`QueryParams` 的全套 List/number 字段：`ids:[]`、`partitionNames:[]`、`generalFilterRoleList:[]`、`limit:0`、`offset:0`、`targetQps:0`、`collectionRule:""`**（缺失会在框架统计阶段 NPE，概率 100%）
    - `annsField`（Search 必填）
    - `nq`、`topK`（Search/Query 必填）
    - `randomVector`（Search 必填）
    - `loadAll`（Load 必填）
  - **重复结构使用引用思路**：当多个组件参数高度相似（如递增并发的多个 SearchParams），只在第一个写完整字段，后续只写与第一个不同的字段（但注意 JSON 本身不支持引用，所以实际仍需完整输出，但应尽量减少不必要字段）。
- 顶层 key 必须为：`<ParamsClassName>_<序号>`，序号从 0 递增即可。
- **所有 List 字段必须显式给出**（哪怕是 `[]`），避免组件里 NPE。
- 所有 Milvus enum 字段必须输出 **正确的枚举常量名**（例如 `VarChar`、`FloatVector`、`AUTOINDEX`、`L2`）。
- **`FieldParams` 中的 boolean 字段建议显式给值**：`autoId`、`partitionKey`、`nullable`、`enableMatch`、`enableAnalyzer` 即使为 `false` 也建议显式写出，避免反序列化歧义。
- **`CreateCollectionParams` 的 `numPartitions` 与 `partitionKey` 约束**：如果 `numPartitions > 0`，必须至少有一个字段的 `partitionKey` 为 `true`；如果 `numPartitions = 0`，所有字段的 `partitionKey` 都应为 `false`。
- **索引类型与环境约束**：如果用户指定了云上环境（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`），**必须使用 `AUTOINDEX`**；本地环境（`devops`、`fouram`）可以使用所有索引类型。
- 未指定 collection 时，`collectionName` 设为 `""`（空字符串），并尽量不要同时填 `collectionRule`。

你也可以让 LLM 一并产出：

- `initial_params`（建议至少 `{ "cleanCollection": false }`）
- `customize_params`（核心）

---

