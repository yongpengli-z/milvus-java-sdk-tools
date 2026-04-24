# AlterAliasParams

修改 Collection 的别名。对应组件：`custom.components.AlterAliasComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `databaseName` | String | 否 | `""` | |
| `collectionName` | String | 否 | `""` | |
| `alias` | String | 是 | `""` | 新别名 |

## 约束

- 用于修改已有别名的 collection 的别名（如需新建别名使用 CreateAliasParams）

## JSON 示例

```json
{"AlterAliasParams_0": {"alias": "new_alias"}}
```
