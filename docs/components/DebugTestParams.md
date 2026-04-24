# DebugTestParams

内部调试/测试用组件。对应组件：`custom.components.DebugTestComp`

> 该组件仅供内部调试使用，普通用户/LLM 生成 JSON 时通常不需要使用。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `test` | String | 是 | | 测试名称/标识符，决定执行哪种调试逻辑 |

## JSON 示例

```json
{"DebugTestParams_0": {"test": "some_debug_test"}}
```
