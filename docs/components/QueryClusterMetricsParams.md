# QueryClusterMetricsParams

通过 Zilliz Cloud RESTful Control Plane V2 接口 `/v2/clusters/{CLUSTER_ID}/metrics/query` 查询实例 metrics。对应组件：`custom.components.QueryClusterMetricsComp`

参考文档：[Query Cluster Metrics (V2)](https://docs.zilliz.com/reference/restful/query-cluster-metrics-v2)

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `baseUrl` | String | 否 | 忽略 | 已废弃；组件不会使用传入值，Control Plane API host 必须按当前运行环境自动解析 |
| `clusterId` | String | 否 | `newInstanceInfo.instanceId` | 目标 cluster/instance ID |
| `apiKey` | String | 否 | 自动获取 | 推荐显式传 Zilliz Cloud API key；为空时会按账号自动查询 managed API key；发送为 `Authorization: Bearer <apiKey>` |
| `apiKeySystemProperty` | String | 否 | `zilliz.apiKey` | 从 JVM property 读取 API key 的 key 名 |
| `accountEmail` | String | 否 | 默认测试账号 | 自动获取 API key 时使用的账号；为空时调用默认账号登录 |
| `accountPassword` | String | 否 | | `accountEmail` 对应密码 |
| `start` | String | 条件 | | UTC ISO 8601 时间；`period` 为空时需要与 `end` 同时传 |
| `end` | String | 条件 | | UTC ISO 8601 时间；`period` 为空时需要与 `start` 同时传 |
| `period` | String | 条件 | | ISO 8601 duration，例如 `PT24H`；传了 `period` 可不传 `start/end` |
| `granularity` | String | 是 | | ISO 8601 duration，例如 `PT30S`、`PT5M`、`PT6H` |
| `dbName` | String | 否 | | 目标 database |
| `collectionName` | String | 否 | | 目标 collection |
| `metricQueries` | List | 条件 | | 指标查询对象列表，例如 `[{"name":"CU_COMPUTATION"}]` |
| `metricNames` | List | 条件 | | 简化写法；`metricQueries` 为空时自动转为对象列表 |
| `headers` | Map | 否 | | 额外 HTTP header，会覆盖默认 header |
| `socketTimeout` | int | 否 | `30000` | HTTP Socket 读取超时（ms） |
| `expectMinDataPoints` | int | 否 | `0` | 大于 0 时校验返回点数不少于该值 |
| `failOnNullValue` | boolean | 否 | `false` | 为 true 时遇到 null metric value 标记失败 |
| `logResponse` | boolean | 否 | `false` | 是否在日志打印完整响应 |

## 常用指标

文档当前枚举包括：

- `CU_COMPUTATION`
- `CU_CAPACITY`
- `STORAGE_USE`
- `READ_VCU`
- `WRITE_VCU`
- `REQ_INSERT_COUNT`
- `REQ_BULK_INSERT_COUNT`
- `REQ_UPSERT_COUNT`
- `REQ_DELETE_COUNT`
- `REQ_SEARCH_COUNT`
- `REQ_QUERY_COUNT`
- `VECTOR_REQ_INSERT_COUNT`
- `VECTOR_REQ_BULK_INSERT_COUNT`
- `VECTOR_REQ_UPSERT_COUNT`
- `VECTOR_REQ_DELETE_COUNT`
- `VECTOR_REQ_SEARCH_COUNT`
- `REQ_INSERT_LATENCY_AVG`
- `REQ_INSERT_LATENCY_P99`
- `REQ_UPSERT_LATENCY_AVG`
- `REQ_UPSERT_LATENCY_P99`
- `REQ_DELETE_LATENCY_AVG`
- `REQ_DELETE_LATENCY_P99`
- `REQ_SEARCH_LATENCY_AVG`
- `REQ_SEARCH_LATENCY_P99`
- `REQ_QUERY_LATENCY_AVG`
- `REQ_QUERY_LATENCY_P99`

## JSON 示例

```json
{
  "QueryClusterMetricsParams_0": {
    "period": "PT24H",
    "granularity": "PT6H",
    "metricNames": ["CU_COMPUTATION", "REQ_SEARCH_COUNT"],
    "expectMinDataPoints": 1
  }
}
```

指定账号，自动获取该账号下的 managed API key：

```json
{
  "QueryClusterMetricsParams_0": {
    "clusterId": "inxx-xxxxxxxxxxxxxxx",
    "accountEmail": "your-account@example.com",
    "accountPassword": "your-password",
    "start": "2024-06-30T16:09:53Z",
    "end": "2024-07-01T16:09:53Z",
    "granularity": "PT6H",
    "metricQueries": [
      {"name": "CU_COMPUTATION"}
    ],
    "expectMinDataPoints": 1,
    "failOnNullValue": false
  }
}
```

鉴权优先级：

1. `apiKey`
2. `-Dzilliz.apiKey`
3. `ZILLIZ_API_KEY`
4. 使用 `accountEmail/accountPassword` 或当前登录态调用 `/cloud/v1/apikey/list-managed-key` 获取 `type=1` 的 managed key
5. 已有实例 token / cloud-service token 兜底

Base URL 解析：

1. 显式传 `baseUrl` 会被忽略，避免测试任务误打 prod
2. 优先从当前环境的 `cloud_service_host` 推导，例如 `https://cloud-service.cloud-uat3.zilliz.com` -> `https://api.cloud-uat3.zilliz.com`
3. `awswest/gcpwest/azurewest/devops/fouram` 默认 `https://api.cloud-uat3.zilliz.com`
4. `alihz/tcbj/hwc` 默认 `https://api.cloud-uat.zilliz.com`
