# PauseParams

主动暂停当前自定义任务。对应组件：`custom.components.PauseComp`

该组件会调用 QTP Server 的 `/customize-task/task/stop?id=<taskId>`，把当前任务状态写成 `STOPPING`，然后轮询任务 Redis 状态。页面点击恢复后，状态变回 `RUNNING`，组件结束并继续执行后续步骤。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `reason` | String | 否 | `null` | 暂停原因，仅用于结果记录 |

## 示例

```json
{"PauseParams_0": {"reason": "manual checkpoint before upgrade"}}
```
