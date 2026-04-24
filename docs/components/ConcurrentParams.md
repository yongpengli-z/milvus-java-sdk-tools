# ConcurrentParams

并行执行一组操作。对应组件：`custom.components.ConcurrentComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `paramComb` | String/Object | 是 | `{}` | 内嵌的 customize_params JSON，内部步骤**并行执行** |

## 约束

- `paramComb` 内部的 key 必须是 `ClassName_index` 格式

## JSON 示例（Insert 和 Search 并行）

```json
{
  "ConcurrentParams_0": {
    "paramComb": {
      "InsertParams_0": {
        "numEntries": 100000, "batchSize": 1000, "numConcurrency": 5,
        "runningMinutes": 10,
        "fieldDataSourceList": [], "generalDataRoleList": []
      },
      "SearchParams_1": {
        "annsField": "vec", "nq": 1, "topK": 10, "outputs": [],
        "numConcurrency": 10, "runningMinutes": 10, "randomVector": true,
        "generalFilterRoleList": [], "partitionNames": []
      }
    }
  }
}
```
