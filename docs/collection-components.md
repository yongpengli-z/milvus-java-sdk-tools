##### 5.1.1 创建 Collection：`CreateCollectionParams`

对应组件：`custom.components.CreateCollectionComp`

字段（`custom.entity.CreateCollectionParams`）：

- **`collectionName`**（string，可空）：不传/空则自动生成随机名。前端默认：`""`。
- **`shardNum`**（int，前端必填）：前端默认：`1`。
- **`numPartitions`**（int，前端必填）：前端默认：`0`。
  - **重要约束**：当 `numPartitions > 0` 时，`fieldParamsList` 中**必须至少有一个字段的 `partitionKey` 为 `true`**，否则 Milvus 会报错：`num_partitions should only be specified with partition key field enabled`。
  - 如果不需要分区，请设置 `numPartitions: 0`，且所有字段的 `partitionKey: false`。
- **`enableDynamic`**（boolean，前端必填）：是否开启动态列。前端默认：`false`。
  - **`$meta` 与 `enableDynamic` 的关系**：当 `enableDynamic: true` 时，Milvus 会自动创建一个名为 `$meta` 的 JSON 类型隐藏字段（Type Params 为 null），用于存储未在 schema 中定义的动态字段数据。**不要在 `fieldParamsList` 中手动定义 `$meta` 字段**，它由 Milvus 自动管理。
  - **LLM 推理规则**：当用户提供的 schema / 截图中出现 `$meta`（JSON 类型、Type Params 为 null）字段时，说明该 collection 开启了动态列，应设置 `enableDynamic: true`，且**不要**将 `$meta` 加入 `fieldParamsList`。
- **`fieldParamsList`**（list，前端必填）：字段定义（见 `FieldParams`）。前端默认：2 个字段（`Int64_0` 作为 PK + `FloatVector_1` 向量字段）。
  - **向量字段约束**：一个 collection **必须至少包含一个向量字段**。向量字段可以是：
    - 正常的顶层向量字段（如 `FloatVector`、`BinaryVector` 等）
    - 或者 Array of Struct 中的向量子字段
  - **示例建议**：在生成示例/demo 时，建议提供一个正常的顶层向量字段（如 `FloatVector`），这样更直观易懂
- **`functionParams`**（object，可空）：function（例如 BM25）配置（见 `FunctionParams`）。前端默认：`{functionType:"", name:"", inputFieldNames:[], outputFieldNames:[]}`（注意 `functionType=""` 是占位，建议用 `null`/不传）。当 `functionParams` 非空时，**`name` 为必填字段**，不能为空字符串，否则创建 collection 会失败。
  - **BM25 约束**：当 `functionType: "BM25"` 时，`inputFieldNames` 中指定的字段**必须在 `fieldParamsList` 中设置 `enableAnalyzer: true`**，否则创建 collection 会失败（报错：`BM25 function input field must set enable_analyzer to true`）。
- **`properties`**（list，前端必填）：collection properties（key/value）。前端默认：`[{propertyKey:"", propertyValue:""}]`（占位；不需要可传 `[]`）。
- **`databaseName`**（string，可空）：前端默认：`""`。

`fieldParamsList` 的元素类型：`custom.entity.FieldParams`

- **`fieldName`**（string）：前端默认模板里是 `Int64_0` / `FloatVector_1`；新增行默认 `""`。
- **`dataType`**（enum 字符串，见下文“DataType 枚举”）：前端默认模板里是 `Int64` / `FloatVector`；新增行默认 `""`（占位，建议不要传空串）。
- **`primaryKey`**（boolean）：注意：Java 字段名是 `isPrimaryKey`，但示例/fastjson 常用 key 为 `primaryKey`。前端默认模板里首字段为 `true`。**建议显式给值**（非主键字段给 `false`）。
- **`autoId`**（boolean）：仅主键字段可用。前端默认：`false`。**建议显式给 `false`**（即使不启用 AutoID）。
- **`dim`**（int）：向量维度（vector 类型必填）。前端默认模板里向量字段为 `768`。
- **`maxLength`**（int）：字符串最大长度（取值范围建议：`1 ~ 65535`），以下场景必填（否则 Milvus 会报 length/max_length 相关错误）：
  - `dataType=VarChar` 或 `dataType=String`
  - `dataType=Array` 且 `elementType=VarChar/String`
  - `dataType=Array` 且 `elementType=Struct`：`structSchema` 中任意 `dataType=VarChar/String` 的子字段也必须填写该子字段的 `maxLength`
  - 前端新增行默认 `null`（占位）；生成 JSON 时请传数字或不传（不要传 `""`）。
- **`maxCapacity`**（int）：Array 必填。前端新增行默认 `null`（占位）；生成 JSON 时请传数字或不传（不要传 `""`）。
- **`elementType`**（enum）：Array 元素类型。前端新增行默认 `null`（占位）；生成 JSON 时请传枚举名或 `null`/不传（不要传 `""`/`0` 占位）。
  - **重要**：当 `dataType=Array` 且 `elementType=Struct` 时，需要同时设置 `structSchema` 字段。
- **`structSchema`**（list，可空）：Struct Schema（仅当 `dataType=Array` 且 `elementType=Struct` 时生效）。用于定义 Array of Struct 中 Struct 的子字段列表。前端默认：`null` 或 `[]`。
  - **Struct 子字段类型**：`custom.entity.StructFieldParams`
    - **`fieldName`**（string）：Struct 子字段名
    - **`dataType`**（enum）：Struct 子字段类型，仅支持以下类型：
      - 整数：`Int8` / `Int16` / `Int32` / `Int64`
      - 浮点：`Float` / `Double`
      - 布尔：`Bool`
      - 字符串：`VarChar` / `String`
      - 向量：`FloatVector`
    - **`dim`**（int）：向量维度（仅 FloatVector 类型生效）
    - **`maxLength`**（int）：VarChar/String 最大长度（仅 VarChar/String 生效）
    - **`isNullable`**（boolean）：是否允许为 NULL（前端默认：`false`）
  - **限制**：
    - Struct 只能作为 Array 的元素类型使用（`dataType=Array`，`elementType=Struct`）
    - Struct 子字段仅支持：Int8/Int16/Int32/Int64、Float/Double、Bool、VarChar/String、FloatVector
    - Struct 可以包含 FloatVector 字段，从而实现 Array of Vector
    - Struct 暂不支持 nullable 字段（但字段中保留该属性以备将来使用）
    - **一个 collection 最多只能有 4 个向量列**（包含 Struct 里的向量列）
- **`partitionKey`**（boolean）：前端默认：`false`。**建议显式给 `false`**（即使不是分区键）。
  - **重要约束**：如果 `CreateCollectionParams.numPartitions > 0`，则**必须至少有一个字段的 `partitionKey` 为 `true`**，否则创建 collection 会失败。
  - 如果 `numPartitions = 0`，则所有字段的 `partitionKey` 都应为 `false`。
- **`nullable`**（boolean）：前端默认：`false`（且主键/向量字段会禁用 nullable=true）。**建议显式给 `false`**（除非确实需要可空）。
- **`enableMatch`**（boolean）：前端默认：`false`。**建议显式给 `false`**（即使不启用匹配功能）。
- **`enableAnalyzer`**（boolean）：前端默认：`false`。**建议显式给 `false`**（即使不启用分析器）。
  - **BM25 约束**：如果该字段是 `functionParams` 中 BM25 function 的**输入字段**（即字段名出现在 `functionParams.inputFieldNames` 中），则**必须设置 `enableAnalyzer: true`**，否则 Milvus 会报错：`BM25 function input field must set enable_analyzer to true`。
- **`analyzerParamsList`**（list，可空）：前端新增行默认 `[{paramsKey:"", paramsValue:""}]`（占位；不需要可传 `[]`）。
  - 每个元素为 `{paramsKey: "xxx", paramsValue: "xxx"}`，最终会被组装为 `Map<String, Object>` 传给 Milvus SDK。
  - **BM25 场景一般使用内置 `standard` 分词器即可**：`[{paramsKey: "type", paramsValue: "standard"}]`。
  - 可选的 analyzer_params 配置方式：

  | 配置方式 | analyzerParamsList 写法 | 说明 |
  |---------|------------------------|------|
  | 内置 standard | `[{"paramsKey":"type","paramsValue":"standard"}]` | 标准分词，**BM25 推荐** |
  | 内置 english | `[{"paramsKey":"type","paramsValue":"english"}]` | 英文分词 + stemming + stop words |
  | 内置 chinese | `[{"paramsKey":"type","paramsValue":"chinese"}]` | 中文 jieba 分词 |
  | 自定义组合 | `[{"paramsKey":"tokenizer","paramsValue":"whitespace"},{"paramsKey":"filter","paramsValue":["lowercase","asciifolding"]}]` | 自定义 tokenizer + filter |

  - **Tokenizer 可选值**：`standard`、`whitespace`、`jieba`
  - **Filter 可选值**：`lowercase`、`asciifolding`、`stemmer`、`stop`、`alphanumonly`、`length`

> **重要提示**：虽然这些 boolean 字段（`autoId`、`partitionKey`、`nullable`、`enableMatch`、`enableAnalyzer`）的前端默认值都是 `false`，但**建议在生成 JSON 时显式给出 `false`**，以避免后端反序列化时因字段缺失导致的不确定性。特别是当 LLM 生成 JSON 时，明确写出这些字段有助于提高可读性和避免歧义。

**Array of Struct 使用示例**：

Array of Struct 允许在一个字段中存储多个结构体元素，每个结构体可以包含多个子字段（包括向量字段），从而实现 Array of Vector 的功能。

**注意**：虽然一个 collection 必须至少包含一个向量字段，且这个向量字段可以是 Array of Struct 中的向量子字段，但在示例中建议同时提供一个正常的顶层向量字段（如下面的 `embedding` 字段），这样更直观易懂。

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id_pk",
        "primaryKey": true,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "Array",
        "fieldName": "clips",
        "elementType": "Struct",
        "maxCapacity": 100,
        "structSchema": [
          {
            "fieldName": "frame_number",
            "dataType": "Int32",
            "isNullable": false
          },
          {
            "fieldName": "clip_embedding",
            "dataType": "FloatVector",
            "dim": 128,
            "isNullable": false
          },
          {
            "fieldName": "clip_desc",
            "dataType": "VarChar",
            "maxLength": 1024,
            "isNullable": false
          },
          {
            "fieldName": "description_embedding",
            "dataType": "FloatVector",
            "dim": 128,
            "isNullable": false
          }
        ],
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      }
    ],
    "functionParams": null,
    "properties": [],
    "databaseName": ""
  }
}
```

**关键点**：
- `dataType` 必须为 `Array`
- `elementType` 必须为 `Struct`
- `structSchema` 必须提供，包含 Struct 的子字段定义
- Struct 子字段可以包含向量类型（如 `FloatVector`），从而实现 Array of Vector
- 数据生成时，`genCommonData` 方法会自动从 `collectionSchema.getStructFields()` 获取 Struct 字段信息并生成对应的数据

**Array of Struct 索引创建**：

**重要**：Array of Struct 中的向量字段**必须建索引**，否则无法进行搜索操作。

为 Struct 中的向量字段创建索引时，需要使用 `fieldName[subFieldName]` 格式（按照 Array 的规则）：

```json
{
  "CreateIndexParams_1": {
    "collectionName": "",
    "databaseName": "",
    "indexParams": [
      {
        "fieldName": "clips[clip_embedding]",
        "indextype": "HNSW",
        "metricType": "MAX_SIM_L2"
      },
      {
        "fieldName": "clips[description_embedding]",
        "indextype": "HNSW",
        "metricType": "MAX_SIM_IP"
      }
    ]
  }
}
```

**注意**：
- **Struct 中的向量字段必须建索引**：如果 Array of Struct 中包含向量字段，必须为这些向量字段创建索引，否则无法进行搜索
- Struct 中的向量字段使用特殊的索引类型和 MetricType（如 `MAX_SIM_L2`、`MAX_SIM_IP`、`MAX_SIM_COSINE`）
- **字段名格式**：`<structFieldName>[<subFieldName>]`（按照 Array 的规则，使用方括号 `[]` 而不是点号 `.`）
  - ✅ 正确：`clips[clip_embedding]`
  - ❌ 错误：`clips.clip_embedding` 或 `clips_clip_embedding`

