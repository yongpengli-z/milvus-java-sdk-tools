# DeleteInstanceParams

删除 Milvus 实例。对应组件：`custom.components.DeleteInstanceComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `useCloudTestApi` | boolean | 是 | `false` | 注意：后端字段名是 `useOPSTestApi` |
| `instanceId` | String | 否 | `""` | 留空使用当前实例 |
| `accountEmail` | String | 否 | `""` | |
| `accountPassword` | String | 否 | `""` | |

## JSON 示例

```json
{"DeleteInstanceParams_0": {"useCloudTestApi": false}}
```
