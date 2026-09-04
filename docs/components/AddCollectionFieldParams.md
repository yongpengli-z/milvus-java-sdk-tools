# AddCollectionFieldParams

动态添加字段到已有 Collection。对应组件：`custom.components.AddCollectionFieldComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `databaseName` | String | 否 | `""` | |
| `fieldName` | String | 否 | `""` | |
| `dataType` | enum | 是 | | DataType 枚举 |
| `defaultValue` | String | 否 | `""` | 按 dataType 解析成对应类型 |
| `enableDefaultValue` | boolean | 是 | `false` | |
| `isNullable` | boolean | 是 | `true` | |
| `isPrimaryKey` | boolean | 否 | | |
| `isPartitionKey` | boolean | 否 | | |
| `isClusteringKey` | boolean | 否 | | |
| `autoID` | boolean | 否 | | |
| `dimension` | int | 否 | | 向量维度 |
| `maxLength` | int | 否 | | VarChar 最大长度 |
| `maxCapacity` | int | 否 | | Array 最大容量 |
| `elementType` | enum | 否 | | Array 元素类型；为 `Struct` 时需同时传 `structSchema` |
| `structSchema` | List<StructFieldParams> | 否 | | 仅 `dataType=Array` 且 `elementType=Struct` 时生效，定义 Struct 子字段；要求 Milvus 3.0.0+，字段必须 `isNullable=true`、`maxCapacity` 必填。子字段不能是 Struct/Array/JSON，可含向量（设 `dim`） |
| `enableAnalyzer` | boolean | 是 | `false` | |
| `enableMatch` | boolean | 是 | `false` | |
| `analyzerParamsList` | List | 否 | `[]` | 不使用建议传 `[]` |

## JSON 示例

```json
{
  "AddCollectionFieldParams_0": {
    "fieldName": "new_field", "dataType": "VarChar", "maxLength": 256,
    "isNullable": true, "enableDefaultValue": false,
    "enableAnalyzer": false, "enableMatch": false, "analyzerParamsList": []
  }
}
```

### 添加 Array of Struct 字段

```json
{
  "AddCollectionFieldParams_0": {
    "fieldName": "chunks", "dataType": "Array", "elementType": "Struct",
    "maxCapacity": 1024, "isNullable": true,
    "structSchema": [
      {"fieldName": "text", "dataType": "VarChar", "maxLength": 512},
      {"fieldName": "text_vector", "dataType": "FloatVector", "dim": 128}
    ],
    "enableDefaultValue": false, "enableAnalyzer": false, "enableMatch": false, "analyzerParamsList": []
  }
}
```

> 注意：Array of Struct 走 `addCollectionStructField` 专用接口（Milvus 3.0.0+），`defaultValue`/`enableDefaultValue` 不生效。
