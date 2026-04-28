# DeleteParams

删除数据。对应组件：`custom.components.DeleteComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `partitionName` | String | 否 | `""` | |
| `ids` | List | 建议必填 | `[]` | 按 ID 删除。不按 ID 删请传 `[]` |
| `filter` | String | 否 | `""` | 按 expr 删除 |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## targetEndpoint

用于 Global Cluster 场景选择 Delete 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

## JSON 示例

```json
{"DeleteParams_0": {"ids": [], "filter": "id_pk < 100", "targetEndpoint": ""}}
```
