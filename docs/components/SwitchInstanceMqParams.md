# SwitchInstanceMqParams

通过 Cloud Ops 提交实例 MQ 切换 workflow。对应组件：`custom.components.SwitchInstanceMqComp`

调用 Cloud Ops：

- `POST /api/v1/ops/resource/instance/mq-transfer/switch`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceId` | String | 否 | 当前任务实例 | Milvus 实例 ID |
| `regionId` | String | 否 | 当前环境 `regionId` | 实例所在 Region |
| `targetMqType` | String | 是 | `""` | 目标 MQ 类型：`woodpecker` / `pulsar` |
| `targetWoodpeckerId` | String | 条件必填 | `""` | `targetMqType=woodpecker` 时填写目标 Woodpecker 集群 ID |

## 注意事项

- QTP 组件只暴露 `woodpecker` / `pulsar` 目标类型。
- 切回 `pulsar` 前会先通过 Cloud Ops preview 检查历史 topic residue；如果需要清理，会先提交 cleanup workflow。
- cleanup workflow 提交后不会立刻生效，组件会重复 preview 并等待历史 topic 不再阻塞后，才提交 switch workflow。
- 最新 zilliz-cloud 实现支持 `pulsar -> woodpecker`、`woodpecker -> 历史 pulsar`、`woodpecker A -> woodpecker B`。
- 组件只提交 workflow 并返回 `taskId` / `processInstanceId`，不等待 workflow 完成。

## JSON 示例

```json
{"SwitchInstanceMqParams_0": {"targetMqType": "woodpecker", "targetWoodpeckerId": "wp-xxx"}}
```
