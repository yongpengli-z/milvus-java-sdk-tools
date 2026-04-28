# SearchParams

向量搜索。对应组件：`custom.components.SearchComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/空 |
| `annsField` | String | **是** | | 向量字段名。**强烈建议显式指定** |
| `nq` | int | 是 | `1` | query vectors 数量 |
| `topK` | int | 是 | `1` | |
| `outputs` | List | 建议必填 | `[]` | 输出字段 |
| `filter` | String | 否 | `""` | Milvus expr（支持 `$fieldName` 占位符） |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | 按时间循环 |
| `randomVector` | boolean | 是 | `true` | |
| `searchLevel` | int | 否 | `1` | |
| `indexAlgo` | String | 否 | `""` | |
| `targetQps` | double | 否 | `0` | |
| `generalFilterRoleList` | List | 否 | `[]` | filter 占位符替换规则。不使用传 `[]` |
| `partitionNames` | List | 否 | `[]` | |
| `ignoreError` | boolean | 否 | `false` | |
| `timeout` | long | 否 | `800` | SDK 请求超时（ms），0=默认 800ms |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## targetEndpoint

用于 Global Cluster 场景选择 Search 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

## Array of Struct 搜索

搜索 Struct 中的向量字段时，`annsField` 格式为 `<structFieldName>[<subFieldName>]`：
- ✅ `clips[clip_embedding]`
- ❌ `clips.clip_embedding`

该向量字段必须已建索引。

## 注意事项

- **性能测试建议**：添加多个 SearchParams 组件，设置不同 `numConcurrency`（1/5/10/20/50）递增压力。

## JSON 示例

```json
{
  "SearchParams_0": {
    "annsField": "vec", "nq": 1, "topK": 10, "outputs": ["*"],
    "numConcurrency": 10, "runningMinutes": 1, "randomVector": true,
    "generalFilterRoleList": [], "partitionNames": [],
    "targetEndpoint": ""
  }
}
```
