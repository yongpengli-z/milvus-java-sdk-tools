# ModifyParams

修改 Milvus 运行时参数。对应组件：`custom.components.ModifyParamsComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `needRestart` | boolean | 是 | `true` | 修改后是否重启 |
| `paramsList` | List | 是 | `[{paramName:"",paramValue:""}]` | 参数列表 |

`paramsList` 元素：`{paramName, paramValue}`

## JSON 示例

```json
{
  "ModifyParams_0": {
    "needRestart": true,
    "paramsList": [
      {"paramName": "log.level", "paramValue": "debug"}
    ]
  }
}
```
