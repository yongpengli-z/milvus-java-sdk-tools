# UseDatabaseParams

切换当前使用的 Database。对应组件：`custom.components.UseDatabaseComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `dataBaseName` | String | 是 | `""` | 注意：前端 key 是 `databaseName`，后端字段名是 `dataBaseName` |

## JSON 示例

```json
{"UseDatabaseParams_0": {"dataBaseName": "my_db"}}
```
