# SwitchInstanceMqParams

通过 Cloud Ops 提交实例 MQ 切换 workflow。对应组件：`custom.components.SwitchInstanceMqComp`

调用 Cloud Ops：

- `POST /api/v1/ops/resource/instance/mq-transfer/switch`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceId` | String | 否 | 当前任务实例 | Milvus 实例 ID |
| `regionId` | String | 否 | 当前环境 `regionId` | 实例所在 Region |
| `targetMqType` | String | 是 | `""` | 目标 MQ 类型：`woodpecker` / `kafka` / `pulsar` |
| `targetWoodpeckerId` | String | 条件必填 | `""` | `targetMqType=woodpecker` 时填写目标 Woodpecker 集群 ID |

## 注意事项

- 最新 zilliz-cloud 实现只支持 `kafka -> woodpecker`、`pulsar -> woodpecker`、`woodpecker -> 历史 kafka/pulsar`、`woodpecker A -> woodpecker B`。
- `kafka <-> pulsar`、`kafka A -> kafka B`、`pulsar A -> pulsar B` 不支持。
- 组件只提交 workflow 并返回 `taskId` / `processInstanceId`，不等待 workflow 完成。

## JSON 示例

```json
{"SwitchInstanceMqParams_0": {"targetMqType": "woodpecker", "targetWoodpeckerId": "wp-xxx"}}
```
