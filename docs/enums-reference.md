### 6. "枚举/可选值"字典（LLM 生成 JSON 必须知道）

#### 6.1 `env`（System Property）

来自 `custom.config.EnvEnum` 的 `region` 字段，可选值（大小写不敏感）：

**本地环境**（支持所有索引类型）：
- `devops`
- `fouram`

**云上环境**：
- `awswest`（AWS 云）
- `gcpwest`（GCP 云）
- `azurewest`（Azure 云）
- `alihz`（阿里云）
- `tcnj`（腾讯云）
- `hwc`（华为云）

> **重要约束 - 索引类型与部署方式**：
> - **Cloud 托管实例**（通过 `CreateInstanceParams` 创建）：**必须使用 `AUTOINDEX`**，不能使用 `HNSW`、`BIN_IVF_FLAT`、`SPARSE_WAND` 等显式索引类型
> - **Helm 部署实例**（通过 `HelmCreateInstanceParams` 创建，无论云上还是本地）：**不能使用 `AUTOINDEX`**，必须使用显式索引类型（如 `HNSW`、`IVF_FLAT`、`SPARSE_WAND` 等）
> - **本地环境**（`devops`、`fouram`）：可以使用所有索引类型

#### 6.2 `fieldDataSourceList`（Insert/Upsert 字段级数据源）

Insert/Upsert 支持为每个字段单独指定数据来源。未配置的字段默认使用 random 生成。

配置格式：`[{"fieldName": "字段名", "dataset": "数据集名称"}, ...]`

可用的数据集（对应 `DatasetEnum`）：

| 数据集 | 格式 | 类型 | 维度 | 路径 |
|--------|------|------|------|------|
| `sift` | NPY | vector | 128 | `/test/milvus/raw_data/sift1b/` |
| `gist` | NPY | vector | 768 | `/test/milvus/raw_data/gist1m/` |
| `deep` | NPY | vector | 96 | `/test/milvus/raw_data/deep1b/` |
| `laion` | NPY | vector | 768 | `/test/milvus/raw_data/laion200M-en/` |
| `bluesky` | JSON Lines | scalar_json | - | `/test/milvus/raw_data/bluesky/` |
| `msmarco-text` | TXT | scalar_text | - | `/test/milvus/raw_data/msmarco_passage_v2/` |

- **向量数据集**（sift/gist/deep/laion）：NPY 格式，用于 FloatVector 字段
- **标量 JSON 数据集**（bluesky）：JSON Lines 格式（每行一个 JSON 对象），用于 JSON 类型字段
- **纯文本数据集**（msmarco-text）：TXT 格式（每行一段纯文本），用于 VarChar 字段（如 BM25 全文检索场景）
- 不配置 `fieldDataSourceList`（或传 `[]`）时，所有字段使用 random 生成

> **使用 `msmarco-text` 数据集的 VarChar 字段注意 `maxLength`**：
>
> msmarco passage 数据集里不少段落超过 2048 字符。如果 `maxLength=2048`（很多示例的默认值），插入到一定量（几万条）后会报：
> ```
> io.milvus.exception.ParamException: Type mismatch for field 'xxx':
>   VarChar field value type must be JsonPrimitive of string,
>   and the string length must shorter than max_length.
> ```
> **建议**：给 `msmarco-text` 喂的 VarChar 字段（尤其是 BM25 analyzer 输入字段）直接设 **`maxLength=65535`**（VarChar 上限）。BM25 tokenizer 对长度无额外要求。

> **bluesky 数据集真实 JSON 结构（写 jsonPath 必查）**：
>
> 数据集是 Bluesky firehose commit 事件的 JSON Lines，每条是一个 commit。典型样本：
> ```json
> {
>   "did": "did:plc:yj3sjq3blzpynh27cumnp5ks",
>   "time_us": 1732206349000167,
>   "kind": "commit",
>   "commit": {
>     "rev": "3lbhtytnn2k2f",
>     "operation": "create",
>     "collection": "app.bsky.feed.post",
>     "rkey": "3lbhtyteurk2y",
>     "record": {
>       "$type": "app.bsky.feed.post",
>       "createdAt": "2024-11-21T16:09:27.095Z",
>       "langs": ["en"],
>       "reply": {"parent": {"cid": "...", "uri": "at://..."}, "root": {"...": "..."}},
>       "text": "some post text"
>     },
>     "cid": "..."
>   }
> }
> ```
>
> **稳定存在的字段 + 适合做 jsonPath 索引的路径**：
>
> | jsonPath | jsonCastType | 说明 |
> |---|---|---|
> | `field["did"]` | `varchar` | 发帖人 DID |
> | `field["time_us"]` | `int64` | 微秒时间戳 |
> | `field["kind"]` | `varchar` | 事件类型（`commit`/`identity`/`account`）|
> | `field["commit"]["operation"]` | `varchar` | `create` / `update` / `delete` |
> | `field["commit"]["collection"]` | `varchar` | `app.bsky.feed.post` 等 |
> | `field["commit"]["record"]["text"]` | `varchar` | 正文（长短不一）|
> | `field["commit"]["record"]["createdAt"]` | `varchar` | ISO 时间字符串 |
>
> **注意**：
> - `reply` 不是每条都有（只有回复帖才有），索引 `reply.*` 前要能接受缺失
> - `commit.record.langs` 是数组，当前 jsonPath 索引不支持 array 元素

#### 6.3 `collectionRule`

代码中支持：

- `random`：从 `globalCollectionNames` 随机选
- `sequence`：按 `*_CollectionIndex` 轮转
- 空/不传：默认使用最后一个 collection（`globalCollectionNames.get(last)`）

#### 6.4 Milvus SDK 枚举（必须用“枚举常量名”）

很多字段是 Milvus SDK 的 enum（fastjson 反序列化时通常要求 **常量名严格匹配**）。

##### 6.4.1 `DataType`（用于 FieldParams / AddCollectionFieldParams）

类型来自 `io.milvus.v2.common.DataType`。代码中实际用到/判断的常见值：

- 标量：`Int64` / `Int32` / `Int16` / `Int8` / `Bool` / `Float` / `Double`
- 字符串：`VarChar` / `String`
- 复杂：`Array` / `JSON` / `Geometry` / `Struct`
- 向量：`FloatVector` / `BinaryVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector` / `SparseFloatVector`

> 注意：
> - 是 **`VarChar`** 不是 `Varchar`。
> - **`Struct`** 只能作为 `Array` 的 `elementType` 使用（即 `dataType=Array`，`elementType=Struct`），不能单独作为字段类型。

##### 6.4.2 `IndexType` / `MetricType`（用于 IndexParams）

类型来自：

- `io.milvus.v2.common.IndexParam.IndexType`
- `io.milvus.v2.common.IndexParam.MetricType`

**重要：索引类型和 MetricType 与字段类型有强约束关系**，LLM 生成 JSON 时必须遵循：

#### 向量字段索引类型

| 向量类型（DataType） | 推荐索引类型（IndexType） | 支持的 MetricType | 说明 |
|---------------------|------------------------|------------------|------|
| `FloatVector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | 最常用的浮点向量，支持所有 MetricType；**仅在云上环境支持 `buildLevel`** |
| `Float16Vector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | 16位浮点向量，**仅在云上环境支持 `buildLevel`** |
| `BFloat16Vector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | BFloat16 向量，**仅在云上环境支持 `buildLevel`** |
| `Int8Vector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | 8位整数向量 |
| `BinaryVector` | `BIN_IVF_FLAT` / `AUTOINDEX` | `HAMMING` / `JACCARD` | 二进制向量，**必须使用 HAMMING 或 JACCARD**，不能使用 L2/IP/COSINE |
| `SparseFloatVector` | `SPARSE_WAND` / `AUTOINDEX` | `IP` / `BM25` | 稀疏向量，**特殊情况**：如果是由 BM25 function 生成的，必须使用 `BM25`；否则使用 `IP`。不能使用 L2/COSINE/HAMMING |
| Array of Struct 中的向量字段 | `HNSW` | `MAX_SIM_L2` / `MAX_SIM_IP` / `MAX_SIM_COSINE` | **必须建索引**。Struct 中的向量字段，字段名格式：`<structFieldName>[<subFieldName]>`（按照 Array 规则使用方括号），例如 `clips[clip_embedding]`。**必须使用 MAX_SIM 系列 MetricType**，不能使用普通的 L2/IP/COSINE |

#### 标量字段索引类型

| 标量类型（DataType） | 推荐索引类型（IndexType） | MetricType | 说明 |
|---------------------|------------------------|-----------|------|
| `VarChar` / `String` | `STL_SORT` / `AUTOINDEX` | 不需要（标量字段不使用 MetricType） | 字符串字段索引，用于加速字符串查询和排序 |
| `Int64` / `Int32` / `Int16` / `Int8` | `STL_SORT` / `AUTOINDEX` | 不需要 | 整数字段索引，用于加速数值查询和排序 |
| `Float` / `Double` | `STL_SORT` / `AUTOINDEX` | 不需要 | 浮点数字段索引，用于加速数值查询和排序 |
| `Bool` | `STL_SORT` / `AUTOINDEX` | 不需要 | 布尔字段索引 |
| `JSON` / Dynamic Field | `STL_SORT` / `AUTOINDEX` | 不需要 | ⚠️ **必须同时设置 `jsonPath` 和 `jsonCastType`**，否则会报错 `json index must specify cast type`。示例：`{"fieldName": "json_field", "indextype": "AUTOINDEX", "jsonPath": "json_field[\"key1\"]", "jsonCastType": "varchar"}` |

**标量字段索引说明**：

- **`STL_SORT`**：标量字段的标准索引类型，用于加速标量字段的查询、排序和过滤
- **`AUTOINDEX`**：系统自动选择索引类型（对于标量字段，通常也会选择 `STL_SORT`）
- **`MetricType`**：标量字段**不需要** MetricType（MetricType 仅用于向量字段的距离计算）
- **JSON 字段索引**：如果对 JSON 字段或 dynamic field 中的标量字段建索引，需要：
  - 设置 `jsonPath`：指定 JSON 路径，例如 `field["key1"]["key2"]`
  - 设置 `jsonCastType`：指定目标类型，例如 `varchar`、`int64`、`double`、`bool`
  - 索引类型：`STL_SORT` 或 `AUTOINDEX`

**代码中的默认映射**（当使用 `AUTOINDEX` 时，系统会根据字段类型自动选择）：

**向量字段**：
- `FloatVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector` → `HNSW` + `L2`
- `BinaryVector` → `BIN_IVF_FLAT` + `HAMMING`
- `SparseFloatVector` → `SPARSE_WAND` + `IP`（如果是由 BM25 function 生成的，则使用 `BM25`）
- Array of Struct 中的向量字段 → `HNSW` + `MAX_SIM_L2`（**必须建索引**，字段名格式：`<structFieldName>[<subFieldName>]`，按照 Array 规则使用方括号）

**标量字段**：
- `VarChar` / `String` / `Int64` / `Int32` / `Int16` / `Int8` / `Float` / `Double` / `Bool` → `STL_SORT`（不需要 MetricType）
- `JSON` / Dynamic Field → `STL_SORT`（需要配合 `jsonPath` 和 `jsonCastType`）

**`buildLevel` 参数**（仅部分向量类型和环境支持）：

- **支持的向量类型**：`FloatVector`、`Float16Vector`、`BFloat16Vector`（仅 float 类型向量）
- **环境约束**：**仅在云上环境**（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`）支持；本地环境（`devops`、`fouram`）不支持
- **不支持的向量类型**：`BinaryVector`、`Int8Vector`、`SparseFloatVector`
- 常用值：`"1"`（Balanced，默认）、`"2"`（Performance）、`"0"`（Memory）

**部署方式与索引类型的约束**：

- **Cloud 托管实例**（通过 `CreateInstanceParams` 创建）：**必须使用 `AUTOINDEX`**，不能使用其他显式索引类型
- **Helm 部署的实例**（通过 `HelmCreateInstanceParams` 创建，无论云上还是本地）：**不能使用 `AUTOINDEX`**，必须使用显式索引类型（如 `HNSW`、`IVF_FLAT`、`BIN_IVF_FLAT`、`SPARSE_WAND` 等）
- **本地环境**（`devops`、`fouram`，非 Helm）：可以使用所有索引类型

**LLM 生成 JSON 时的建议**：

1. **根据部署方式选择索引类型**：
   - 如果用户使用 **Cloud 托管实例**（通过 `CreateInstanceParams` 创建）→ **强制使用 `AUTOINDEX`**
   - 如果用户使用 **Helm 部署的实例**（通过 `HelmCreateInstanceParams` 创建）→ **不能使用 `AUTOINDEX`**，必须使用显式索引类型（如 `HNSW`、`IVF_FLAT`、`SPARSE_WAND` 等）
   - 如果用户使用本地环境（`devops`、`fouram`）且非 Helm → 可以使用所有索引类型
   - 如果用户没指定部署方式 → 需要询问或根据上下文推断

2. **向量字段索引类型选择**：
   - 如果用户指定了向量类型但没指定索引类型：
     - `FloatVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector` → 使用 `AUTOINDEX` + `L2`
     - `BinaryVector` → 使用 `AUTOINDEX` + `HAMMING`（**不能使用 L2**）
     - `SparseFloatVector` → 使用 `AUTOINDEX` + `IP`（**特殊情况**：如果是由 BM25 function 生成的，则使用 `BM25`；**不能使用 L2**）
     - Array of Struct 中的向量字段 → 使用 `HNSW` + `MAX_SIM_L2`（**必须建索引**，字段名格式：`<structFieldName>[<subFieldName>]`，按照 Array 规则使用方括号）
   - 如果用户指定了向量类型但没指定 MetricType：
     - 根据上表自动选择对应的 MetricType
     - **特殊情况**：对于 `SparseFloatVector`，需要检查 `CreateCollectionParams.functionParams`：
       - 如果 `functionParams.functionType == "BM25"` 且该 `SparseFloatVector` 字段名在 `functionParams.outputFieldNames` 中 → 使用 `BM25`
       - 否则 → 使用 `IP`
     - **Array of Struct 中的向量字段**：
       - **必须建索引**：Array of Struct 中的向量字段必须建索引，否则无法进行搜索
       - 字段名格式：`<structFieldName>[<subFieldName>]`（按照 Array 规则使用方括号），例如 `clips[clip_embedding]`
       - MetricType：必须使用 `MAX_SIM_L2`、`MAX_SIM_IP` 或 `MAX_SIM_COSINE`（**不能使用普通的 L2/IP/COSINE**）

3. **标量字段索引类型选择**：
   - 如果用户需要对标量字段建索引（用于加速查询/排序）：
     - `VarChar` / `String` / `Int64` / `Int32` / `Int16` / `Int8` / `Float` / `Double` / `Bool` → 使用 `STL_SORT` 或 `AUTOINDEX`
     - **注意**：标量字段**不需要 MetricType**（MetricType 仅用于向量字段）
   - JSON 字段索引：
     - 如果对 JSON 字段或 dynamic field 中的标量字段建索引，必须设置 `jsonPath` 和 `jsonCastType`
     - 示例：`{"fieldName": "json_field", "indextype": "STL_SORT", "jsonPath": "field[\"key1\"]", "jsonCastType": "varchar"}`

4. **常见错误**：
   - ❌ **Cloud 托管实例使用 `HNSW`** → 应该用 `AUTOINDEX`
   - ❌ **Helm 部署的实例使用 `AUTOINDEX`** → 应该用显式索引类型（如 `HNSW`、`IVF_FLAT`、`SPARSE_WAND` 等）
   - ❌ `BinaryVector` 使用 `L2` → 应该用 `HAMMING`
   - ❌ `SparseFloatVector` 使用 `L2` → 应该用 `IP`（或 `BM25`，如果是由 BM25 function 生成的）
   - ❌ BM25 function 生成的 `SparseFloatVector` 使用 `IP` → 应该用 `BM25`
   - ❌ 本地环境使用 `buildLevel` → `buildLevel` 仅在云上环境支持
   - ❌ `BinaryVector` 或 `SparseFloatVector` 使用 `buildLevel` → `buildLevel` 仅支持 `FloatVector`、`Float16Vector`、`BFloat16Vector`
   - ❌ `BinaryVector` 使用 `HNSW` → 应该用 `BIN_IVF_FLAT`
   - ❌ 标量字段设置 `MetricType` → 标量字段不需要 MetricType
   - ❌ JSON 字段索引缺少 `jsonPath` 或 `jsonCastType` → JSON 字段索引必须指定路径和类型
   - ❌ Array of Struct 中的向量字段使用普通 `L2`/`IP`/`COSINE` → 应该用 `MAX_SIM_L2`/`MAX_SIM_IP`/`MAX_SIM_COSINE`
   - ❌ Array of Struct 字段名格式错误 → 应该用 `clips[clip_embedding]` 格式（按照 Array 规则使用方括号），而不是 `clips.clip_embedding` 或 `clips_clip_embedding`
   - ❌ Array of Struct 中的向量字段未建索引 → **必须建索引**，否则无法进行搜索

##### 6.4.3 `FunctionType`（用于 FunctionParams）

类型来自 `io.milvus.common.clientenum.FunctionType`，常见：`BM25`（用于稀疏向量/文本检索）。

---

