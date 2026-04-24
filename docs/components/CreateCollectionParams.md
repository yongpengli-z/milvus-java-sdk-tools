# CreateCollectionParams

创建 Milvus Collection。对应组件：`custom.components.CreateCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 不传/空则自动生成随机名 |
| `shardNum` | int | 是 | `1` | Shard 数量 |
| `numPartitions` | int | 是 | `0` | Partition Key 分区数。>0 时必须有字段 `partitionKey=true` |
| `enableDynamic` | boolean | 是 | `false` | 是否开启动态列 |
| `fieldParamsList` | List | 是 | 2字段模板 | 字段定义列表（见 FieldParams） |
| `functionParams` | Object | 否 | `null` | Function 配置（见 FunctionParams） |
| `properties` | List | 是 | `[]` | Collection properties（key/value 列表） |
| `databaseName` | String | 否 | `""` | 目标 database |

## 约束

- **numPartitions 与 partitionKey**：`numPartitions > 0` 时，`fieldParamsList` 中**必须至少有一个字段 `partitionKey: true`**，否则报错 `num_partitions should only be specified with partition key field enabled`。`numPartitions = 0` 时所有字段 `partitionKey` 都应为 `false`。
- **向量字段**：一个 collection **必须至少包含一个向量字段**（可以是顶层字段或 Array of Struct 中的向量子字段）。
- **最多 4 个向量列**（包含 Struct 里的向量列）。

## enableDynamic 与 $meta

当 `enableDynamic: true` 时，Milvus 自动创建名为 `$meta` 的 JSON 隐藏字段，用于存储 schema 未定义的动态字段数据。**不要在 `fieldParamsList` 中手动定义 `$meta`**。

**LLM 推理规则**：当用户 schema/截图中出现 `$meta`（JSON 类型、Type Params 为 null），说明开启了动态列，应设 `enableDynamic: true` 且不将 `$meta` 加入 `fieldParamsList`。

## FieldParams 子结构

`fieldParamsList` 的元素类型：`custom.entity.FieldParams`

| 字段 | 类型 | 说明 |
|------|------|------|
| `fieldName` | String | 字段名 |
| `dataType` | enum | DataType 枚举（见 README） |
| `primaryKey` | boolean | 是否主键。**建议显式给值** |
| `autoId` | boolean | 仅主键可用。**建议显式给 `false`** |
| `dim` | int | 向量维度（vector 类型必填） |
| `maxLength` | int | VarChar/String 必填（1~65535）；Array elementType=VarChar 也需要 |
| `maxCapacity` | int | Array 必填 |
| `elementType` | enum | Array 元素类型。`Struct` 时需同时设 `structSchema` |
| `structSchema` | List | Array of Struct 的子字段定义（见 StructFieldParams） |
| `partitionKey` | boolean | **建议显式给 `false`** |
| `nullable` | boolean | **建议显式给 `false`**（主键/向量不可为 true） |
| `enableMatch` | boolean | **建议显式给 `false`**。true 时**必须同时 `enableAnalyzer: true`** |
| `enableAnalyzer` | boolean | **建议显式给 `false`** |
| `analyzerParamsList` | List | 分析器配置。**不使用时传 `[]`**（避免 NPE） |

### enableMatch 与 enableAnalyzer 联动

- `enableMatch: true` → **必须** `enableAnalyzer: true` + 配置 `analyzerParamsList`，否则报错 `field which has enable_match must also enable_analyzer`
- BM25 的 `inputFieldNames` 中的字段 → **必须** `enableAnalyzer: true`

### analyzerParamsList 配置

每个元素为 `{paramsKey, paramsValue}`，组装为 `Map<String, Object>`。

| 配置方式 | 写法 | 说明 |
|---------|------|------|
| 内置 standard | `[{"paramsKey":"type","paramsValue":"standard"}]` | **BM25 推荐** |
| 内置 english | `[{"paramsKey":"type","paramsValue":"english"}]` | 英文分词 |
| 内置 chinese | `[{"paramsKey":"type","paramsValue":"chinese"}]` | 中文 jieba |
| 自定义 | `[{"paramsKey":"tokenizer","paramsValue":"whitespace"},{"paramsKey":"filter","paramsValue":["lowercase"]}]` | 自定义组合 |

- Tokenizer 可选值：`standard`、`whitespace`、`jieba`
- Filter 可选值：`lowercase`、`asciifolding`、`stemmer`、`stop`、`alphanumonly`、`length`

## StructFieldParams 子结构

仅当 `dataType=Array` 且 `elementType=Struct` 时使用。

| 字段 | 类型 | 说明 |
|------|------|------|
| `fieldName` | String | 子字段名 |
| `dataType` | enum | 仅支持：Int8/Int16/Int32/Int64、Float/Double、Bool、VarChar/String、FloatVector |
| `dim` | int | 仅 FloatVector 生效 |
| `maxLength` | int | 仅 VarChar/String 生效 |
| `isNullable` | boolean | 默认 `false` |

**限制**：Struct 只能作为 Array 元素使用；Struct 可包含 FloatVector 实现 Array of Vector。

## FunctionParams 子结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `functionType` | String | 如 `"BM25"` |
| `name` | String | **必填**，不能为空字符串 |
| `inputFieldNames` | List | 输入字段名列表 |
| `outputFieldNames` | List | 输出字段名列表 |

**BM25 约束**：`inputFieldNames` 中的字段**必须** `enableAnalyzer: true`，否则报错 `BM25 function input field must set enable_analyzer to true`。

## Array of Struct 示例

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64", "fieldName": "id_pk",
        "primaryKey": true, "autoId": false, "partitionKey": false,
        "nullable": false, "enableMatch": false, "enableAnalyzer": false, "analyzerParamsList": []
      },
      {
        "dataType": "Array", "fieldName": "clips", "elementType": "Struct", "maxCapacity": 100,
        "structSchema": [
          {"fieldName": "frame_number", "dataType": "Int32", "isNullable": false},
          {"fieldName": "clip_embedding", "dataType": "FloatVector", "dim": 128, "isNullable": false},
          {"fieldName": "clip_desc", "dataType": "VarChar", "maxLength": 1024, "isNullable": false}
        ],
        "primaryKey": false, "autoId": false, "partitionKey": false,
        "nullable": false, "enableMatch": false, "enableAnalyzer": false, "analyzerParamsList": []
      }
    ],
    "functionParams": null,
    "properties": []
  }
}
```

## BM25 全文检索示例

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64", "fieldName": "id_pk",
        "primaryKey": true, "autoId": false, "partitionKey": false,
        "nullable": false, "enableMatch": false, "enableAnalyzer": false, "analyzerParamsList": []
      },
      {
        "dataType": "VarChar", "fieldName": "text_content", "maxLength": 65535,
        "primaryKey": false, "autoId": false, "partitionKey": false,
        "nullable": false, "enableMatch": false, "enableAnalyzer": true,
        "analyzerParamsList": [{"paramsKey": "type", "paramsValue": "standard"}]
      },
      {
        "dataType": "SparseFloatVector", "fieldName": "sparse_embedding",
        "primaryKey": false, "autoId": false, "partitionKey": false,
        "nullable": false, "enableMatch": false, "enableAnalyzer": false, "analyzerParamsList": []
      }
    ],
    "functionParams": {
      "functionType": "BM25",
      "name": "bm25_func",
      "inputFieldNames": ["text_content"],
      "outputFieldNames": ["sparse_embedding"]
    },
    "properties": []
  }
}
```
