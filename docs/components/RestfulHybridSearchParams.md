# RestfulHybridSearchParams

通过 RESTful 接口 `/v2/vectordb/entities/advanced_search` 发起混合搜索。对应组件：`custom.components.RestfulHybridSearchComp`

## 参数

与 HybridSearchParams 对齐，额外字段：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | |
| `searchRequests` | List | 是 | | 同 HybridSearchParams |
| `ranker` | String | 是 | `"RRF"` | |
| `rankerParams` | Object | 否 | | |
| `topK` | int | 是 | `10` | |
| `nq` | int | 是 | `1` | |
| `randomVector` | boolean | 是 | `true` | |
| `outputs` | List | 建议必填 | `[]` | |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | |
| `targetQps` | double | 否 | `0` | |
| `generalFilterRoleList` | List | 否 | `[]` | |
| `ignoreError` | boolean | 否 | `false` | |
| `socketTimeout` | int | 否 | `5000` | HTTP Socket 读取超时（ms） |

## 注意事项

- `ranker` 发请求前自动转小写（`RRF`→`rrf`）
- Float16Vector/BFloat16Vector 序列化为 float 数组（非 base64）
- 走 SDK 请继续使用 HybridSearchParams；仅 REST 通道测试时才用本组件

## JSON 示例

```json
{
  "RestfulHybridSearchParams_0": {
    "searchRequests": [
      {"annsField": "float_vec", "topK": 10, "searchParams": {"level": 1}, "filter": ""},
      {"annsField": "sparse_vec", "topK": 10, "searchParams": {}, "filter": ""}
    ],
    "ranker": "RRF", "rankerParams": {"k": 60},
    "topK": 10, "nq": 1, "randomVector": true, "outputs": ["*"],
    "numConcurrency": 10, "runningMinutes": 1,
    "generalFilterRoleList": [], "socketTimeout": 5000
  }
}
```
