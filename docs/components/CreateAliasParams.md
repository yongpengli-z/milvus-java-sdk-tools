# CreateAliasParams

为 Collection 创建别名。对应组件：`custom.components.CreateAliasComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `databaseName` | String | 否 | `""` | |
| `collectionName` | String | 否 | `""` | |
| `alias` | String | 是 | `""` | 别名 |

## 约束

- 一个 collection 只能有一个别名
- 如果 collection 已有别名，必须使用 `AlterAliasParams` 修改，不能再用 `CreateAliasParams`

## JSON 示例

```json
{"CreateAliasParams_0": {"alias": "my_alias"}}
```
