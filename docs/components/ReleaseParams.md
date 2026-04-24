# ReleaseParams

释放 Collection 内存。对应组件：`custom.components.ReleaseCollectionComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `releaseAll` | boolean | 是 | `false` | true 则释放所有 collection |
| `collectionName` | String | 否 | `""` | `releaseAll=false` 时使用 |

## JSON 示例

```json
{"ReleaseParams_0": {"releaseAll": false}}
```
