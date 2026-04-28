# GetParams

按 ID 获取实体（类似 KV get）。对应组件：`custom.components.GetComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 为空使用最后一个 collection |
| `databaseName` | String | 否 | `""` | |
| `ids` | List | 是 | | 要获取的 ID 列表，如 `[1, 2, 3]` 或 `["id_001"]` |
| `outputFields` | List | 否 | `[]` | 输出字段列表 |
| `partitionNames` | List | 否 | `[]` | |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## targetEndpoint

用于 Global Cluster 场景选择 Get 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

## JSON 示例

```json
{"GetParams_0": {"ids": [1, 2, 3], "outputFields": ["*"], "partitionNames": [], "targetEndpoint": ""}}
```
