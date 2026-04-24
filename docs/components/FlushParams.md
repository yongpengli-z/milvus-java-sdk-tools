# FlushParams

将 growing segment 刷成 sealed segment，确保数据落盘并触发索引构建。对应组件：`custom.components.FlushComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `flushAll` | boolean | 是 | `false` | true 则 flush 所有 collection |
| `collectionName` | String | 否 | `""` | `flushAll=false` 时使用 |

## 使用场景

- Insert 完成后立即 Flush，确保数据落盘
- 在并发读写测试中，Flush 应在 Insert 之后、Search/Query 之前执行
- 仅测试写入性能时，Insert 后立即 Flush

## JSON 示例

```json
{"FlushParams_0": {"flushAll": false}}
```
