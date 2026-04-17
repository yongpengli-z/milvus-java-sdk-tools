##### 5.1.2 建索引：`CreateIndexParams`

对应组件：`custom.components.CreateIndexComp`

字段（`custom.entity.CreateIndexParams`）：

- **`collectionName`**（string，可空）：默认最后一个 collection
- **`databaseName`**（string，可空）
- **`indexParams`**（list，建议必填，可为 `[]`）

`indexParams` 的元素类型：`custom.entity.IndexParams`

- **`fieldName`**（string）：要建索引的字段名（注意不是 `filedName`）
- **`indextype`**（enum 字符串）：`io.milvus.v2.common.IndexParam.IndexType`
  - 注意：前端 `createIndexEdit.vue` 的 key 是 `indexType`，但后端字段名是 `indextype`；**请确保最终 JSON 使用 `indextype`**（或在你的参数生成/映射层做转换），否则会导致索引类型为空。
  - **重要约束**：索引类型与向量类型有强约束关系（见下文"向量类型与索引类型/MetricType 约束"）。
- **`metricType`**（enum 字符串，可空）：`io.milvus.v2.common.IndexParam.MetricType`
  - **重要约束**：MetricType 与向量类型有强约束关系（见下文"向量类型与索引类型/MetricType 约束"）。
- **`jsonPath/jsonCastType`**（string，可空）：用于 JSON 字段或 dynamic field 的标量字段索引
  - `jsonPath`：JSON 路径，例如 `field["key1"]["key2"]`、`field["key1"][0]["key2"]`
  - `jsonCastType`：目标类型，例如 `varchar`、`int64`、`double`、`bool`
  - **使用场景**：对 JSON 字段或 dynamic field 中的标量字段建索引时，必须同时设置这两个字段
  - **索引类型**：通常使用 `STL_SORT` 或 `AUTOINDEX`（不需要 MetricType）
- **`buildLevel`**（string，可空）：例如 HNSW build level（仅部分向量类型和环境使用）
  - **支持范围**：仅 `FloatVector`、`Float16Vector`、`BFloat16Vector` 支持 `buildLevel` 参数
  - **环境约束**：**仅在云上环境**（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`）支持 `buildLevel` 参数；本地环境（`devops`、`fouram`）不支持
  - 常用值：`"1"`（Balanced，默认）、`"2"`（Performance）、`"0"`（Memory）

> 重要：如果你想"让系统自动给所有向量字段建索引"，请传 `indexParams: []`（不要省略该字段）。系统会根据向量类型自动选择对应的索引类型和 MetricType。

**向量类型与索引类型/MetricType 约束**（LLM 生成 JSON 时必须遵循）：

- **`FloatVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector`**：
  - 推荐索引类型：`AUTOINDEX` 或 `HNSW`
  - 支持的 MetricType：`L2`（默认）、`COSINE`、`IP`
  - 支持 `buildLevel`：仅 `FloatVector` / `Float16Vector` / `BFloat16Vector`，且**仅在云上环境**（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`）支持；本地环境不支持

- **`BinaryVector`**：
  - 推荐索引类型：`AUTOINDEX` 或 `BIN_IVF_FLAT`
  - **必须使用的 MetricType**：`HAMMING` 或 `JACCARD`（**不能使用 L2/IP/COSINE**）
  - 不支持 `buildLevel`

- **`SparseFloatVector`**：
  - 推荐索引类型：`AUTOINDEX` 或 `SPARSE_WAND`
  - **MetricType 选择规则**（**特殊情况**）：
    - 如果 `SparseFloatVector` 字段是由 **BM25 function** 生成的（即该字段在 `CreateCollectionParams.functionParams.outputFieldNames` 中），**必须使用 `BM25`**
    - 否则，使用 `IP`
    - **不能使用**：`L2`、`COSINE`、`HAMMING`
  - 不支持 `buildLevel`
  - **示例**：
    - BM25 function 生成的稀疏向量：`{"fieldName": "sparse_embedding", "indextype": "AUTOINDEX", "metricType": "BM25"}`
    - 普通稀疏向量：`{"fieldName": "sparse_vec", "indextype": "AUTOINDEX", "metricType": "IP"}`

- **Array of Struct 中的向量字段**（**特殊索引类型**）：
  - **必须建索引**：Array of Struct 中的向量字段**必须建索引**，否则无法进行搜索操作
  - **字段名格式**：`<structFieldName>[<subFieldName>]`（按照 Array 规则使用方括号 `[]`），例如 `clips[clip_embedding]`
    - ✅ 正确：`clips[clip_embedding]`
    - ❌ 错误：`clips.clip_embedding` 或 `clips_clip_embedding`
  - **索引类型**：`HNSW`（当前支持）
  - **MetricType**：`MAX_SIM_L2`、`MAX_SIM_IP`、`MAX_SIM_COSINE`（**必须使用 MAX_SIM 系列**，不能使用普通的 L2/IP/COSINE）
  - **说明**：Array of Struct 中的向量字段使用 Embedding List Index（嵌入列表索引），用于搜索包含多个向量的记录
  - **示例**：
    ```json
    {
      "fieldName": "clips[clip_embedding]",
      "indextype": "HNSW",
      "metricType": "MAX_SIM_L2"
    }
    ```

**标量字段索引类型**（LLM 生成 JSON 时必须遵循）：

- **`VarChar` / `String` / `Int64` / `Int32` / `Int16` / `Int8` / `Float` / `Double` / `Bool`**：
  - 推荐索引类型：`STL_SORT` 或 `AUTOINDEX`
  - **不需要 MetricType**（MetricType 仅用于向量字段）
  - 用途：加速标量字段的查询、排序和过滤

- **`JSON` / Dynamic Field**：
  - 推荐索引类型：`STL_SORT` 或 `AUTOINDEX`
  - ⚠️ **必须同时设置 `jsonPath` 和 `jsonCastType`**，缺少任何一个都会报错：`json index must specify cast type`
  - **不需要 MetricType**
  - `jsonPath` 格式：`字段名["key"]`，例如 `meta_json["category"]`、`meta_json["age"]`
  - `jsonCastType` 可选值：`varchar`、`int64`、`double`、`bool`
  - ✅ 正确：`{"fieldName": "meta_json", "indextype": "AUTOINDEX", "jsonPath": "meta_json[\"category\"]", "jsonCastType": "varchar"}`
  - ❌ 错误：`{"fieldName": "meta_json", "indextype": "AUTOINDEX"}` — 缺少 jsonPath 和 jsonCastType

详细约束表见下文"6.4.2 IndexType / MetricType"章节。

##### 5.1.3 Load：`LoadParams`

对应组件：`custom.components.LoadCollectionComp`

字段：

- **`loadAll`**（boolean，前端必填）：true 则加载实例内所有 collection。前端默认 `false`。
- **`collectionName`**（string，可空）：`loadAll=false` 时使用。前端默认 `""`。
- **`loadFields`**（list，建议必填）：不加载全部字段时指定字段名列表；不指定请传 `[]`。前端默认 `[]`（页面用 `tempLoadFields` 字符串输入再 split）。
- **`skipLoadDynamicField`**（boolean，前端必填）：前端默认 `false`。

