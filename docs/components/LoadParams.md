# LoadParams

加载 Collection 到内存。对应组件：`custom.components.LoadCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `loadAll` | boolean | 是 | `false` | true 则加载实例内所有 collection |
| `collectionName` | String | 否 | `""` | `loadAll=false` 时使用 |
| `loadFields` | List | 建议必填 | `[]` | 部分加载字段列表；不使用传 `[]` |
| `skipLoadDynamicField` | boolean | 是 | `false` | 是否跳过加载动态字段 |

## JSON 示例

```json
{"LoadParams_0": {"loadAll": false, "loadFields": [], "skipLoadDynamicField": false}}
```
