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
| `elementType` | enum | 否 | | Array 元素类型 |
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
