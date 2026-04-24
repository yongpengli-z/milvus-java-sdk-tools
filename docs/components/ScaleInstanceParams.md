# ScaleInstanceParams

升降配 Milvus 实例的 CU 规格和/或副本数。对应组件：`custom.components.ScaleInstanceComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceId` | String | 否 | `""` | 留空使用当前实例 |
| `targetCuType` | String | 否 | `""` | 目标 CU（如 `class-8-enterprise`），留空不修改 |
| `targetReplica` | int | 否 | `1` | 目标副本数，0=不修改 |
| `accountEmail` | String | 否 | `""` | |
| `accountPassword` | String | 否 | `""` | |

## classId 与 replica 编码规则

- replica=1 时省略：`class-8-enterprise`
- replica≥2 时插在 CU 数后面：`class-8-2-enterprise`、`class-8-3-disk-enterprise`

## 约束

- `replica > 1` 时 CU 必须 `≥ 8`
- CU 和 replica 同时变更时，后端自动决定操作顺序：
  - 升配：先升 CU → 再加 replica
  - 降配：先减 replica → 再降 CU

## JSON 示例

```json
{
  "ScaleInstanceParams_0": {
    "targetCuType": "class-8-enterprise",
    "targetReplica": 2
  }
}
```
