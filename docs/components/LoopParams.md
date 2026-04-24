# LoopParams

循环执行一组操作。对应组件：`custom.components.LoopComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `paramComb` | String/Object | 是 | `{}` | 内嵌的 customize_params JSON |
| `runningMinutes` | int | 是 | `0` | 循环总时长上限（分钟），0=无限 |
| `cycleNum` | int | 是 | `1` | 循环次数上限，0=无限 |

## 使用场景

- **批量创建多个 collection**：需要创建 50 个 collection 并插入数据时，用 LoopParams 循环，不要生成 50 个重复的组件
- **重复执行操作序列**：如反复 创建→建索引→加载→插入→搜索

## 约束

- `paramComb` 内部的 key 也必须是 `ClassName_index` 格式（内部也按 `_数字` 排序执行）
- 搭配 `collectionRule` 使用：内部组件 `collectionRule: "random"` 或 `"sequence"` 可让每次循环操作不同的 collection

## JSON 示例（批量创建 50 个 collection 并插入数据）

```json
{
  "LoopParams_0": {
    "cycleNum": 50,
    "runningMinutes": 0,
    "paramComb": {
      "CreateCollectionParams_0": {
        "shardNum": 1, "numPartitions": 0, "enableDynamic": false,
        "fieldParamsList": [
          {"dataType":"Int64","fieldName":"id_pk","primaryKey":true,"autoId":false,"partitionKey":false,"nullable":false,"enableMatch":false,"enableAnalyzer":false,"analyzerParamsList":[]},
          {"dataType":"FloatVector","fieldName":"vec","dim":128,"primaryKey":false,"autoId":false,"partitionKey":false,"nullable":false,"enableMatch":false,"enableAnalyzer":false,"analyzerParamsList":[]}
        ],
        "properties": []
      },
      "CreateIndexParams_1": {
        "indexParams": [{"fieldName":"vec","indextype":"AUTOINDEX","metricType":"L2"}]
      },
      "LoadParams_2": {"loadAll": false, "loadFields": [], "skipLoadDynamicField": false},
      "InsertParams_3": {
        "numEntries": 10000, "batchSize": 1000, "numConcurrency": 1,
        "fieldDataSourceList": [], "generalDataRoleList": []
      }
    }
  }
}
```
