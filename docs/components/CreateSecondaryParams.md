# CreateSecondaryParams

为已有实例添加 Secondary 集群（GDN 全球数据网络）。对应组件：`custom.components.CreateSecondaryComp`

## 场景

- **场景 A**：将独立实例转换为 Global Cluster（传 `instanceId`）
- **场景 B**：在已有 Global Cluster 上扩展新 Secondary（传 `globalClusterId`）

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceId` | String | 否 | `""` | 场景 A 使用 |
| `globalClusterId` | String | 否 | `""` | 场景 B 使用 |
| `secondaryClusters` | List | 是 | | Secondary 集群列表 |
| `waitTimeoutMinutes` | int | 否 | `30` | 等待超时（分钟） |

### secondaryClusters 元素

| 字段 | 类型 | 说明 |
|------|------|------|
| `regionId` | String | 目标 Region ID |
| `instanceName` | String | Secondary 名称 |
| `classId` | String | CU 规格 |
| `replica` | int | 副本数，默认 `1` |

## JSON 示例

```json
{
  "CreateSecondaryParams_0": {
    "instanceId": "",
    "secondaryClusters": [
      {"regionId": "aws-us-west-2", "instanceName": "secondary-1", "classId": "class-1-enterprise", "replica": 1}
    ],
    "waitTimeoutMinutes": 30
  }
}
```
