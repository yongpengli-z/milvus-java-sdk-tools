# RestfulSearchParams

通过 Milvus RESTful 接口发起向量搜索。对应组件：`custom.components.RestfulSearchComp`

## 参数

与 SearchParams 基本一致，区别在请求通道（HTTP vs SDK）。

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | |
| `annsField` | String | 是 | | 向量字段名 |
| `nq` | int | 是 | `1` | |
| `topK` | int | 是 | `1` | |
| `outputs` | List | 建议必填 | `[]` | |
| `filter` | String | 否 | `""` | |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | |
| `randomVector` | boolean | 是 | `true` | |
| `targetQps` | double | 否 | `0` | |
| `generalFilterRoleList` | List | 否 | `[]` | |
| `ignoreError` | boolean | 否 | `false` | |
| `socketTimeout` | int | 否 | `5000` | HTTP Socket 读取超时（ms） |

## JSON 示例

```json
{
  "RestfulSearchParams_0": {
    "annsField": "vec", "nq": 1, "topK": 10, "outputs": ["*"],
    "numConcurrency": 10, "runningMinutes": 1, "randomVector": true,
    "generalFilterRoleList": []
  }
}
```
