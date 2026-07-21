# ChaosMeshParams

在有 Chaos Mesh CRD 的 Kubernetes 集群中创建或删除一次受限的故障实验。对应组件：`custom.components.ChaosMeshComp`。创建成功的实验会在当前场景最外层执行结束时自动删除；无论场景正常结束、步骤失败还是任务终止，调度器都会尝试清理。

组件使用当前 `env` 的 kubeconfig；也可以通过 JVM 参数 `-Dkubeconfig=/path/to/config` 覆盖。

## 安全约束

- `create` 必须指定目标实例（`namespace`、`instanceId`，或前序步骤创建的全局实例）、正数 `duration` 和至少一个 `labelSelectors`。
- 未传 `namespace` 时，组件使用 `milvus-<instanceId>`；未传 `instanceId` 时，会使用前序 `CreateInstanceParams` 写入的全局 instance ID。
- 组件将 `spec.selector.namespaces` 固定为最终解析出的 namespace，不能跨 namespace 注入故障。
- 初始支持 `PodChaos`、`NetworkChaos`、`StressChaos`、`TimeChaos` 和 `IOChaos`。
- 测试场景结束时会自动删除本场景成功创建的 CR；如需提前停止故障，仍可追加 `delete` 步骤。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `operation` | String | 否 | `create` | `create` 或 `delete`。 |
| `kind` | String | 是 | — | Chaos Mesh 类型：`PodChaos` / `NetworkChaos` / `StressChaos` / `TimeChaos` / `IOChaos`。 |
| `name` | String | delete 时是 | 自动生成 | Chaos Mesh Custom Resource 名称；create 留空时后端生成唯一名称，并在结果中返回。 |
| `instanceId` | String | 否 | `""` | Milvus instance ID；未传 `namespace` 时自动转换为 `milvus-<instanceId>`。为空时回退到前序步骤创建的全局实例。 |
| `namespace` | String | 否 | `""` | CR 所在 namespace，同时也是被注入的目标 namespace。显式传入时优先级最高。 |
| `duration` | String | create 时是 | — | Go duration，例如 `60s`、`2m`、`1h30m`。 |
| `labelSelectors` | Map<String, String> | create 时是 | `{}` | 目标 Pod 标签；至少填写一项。 |
| `attributes` | Map<String, Object> | 否 | `{}` | 类型专属的 `spec` 字段，例如 PodChaos 的 `action`，NetworkChaos 的 `delay` / `loss`。不能覆盖组件生成的 `duration`、`selector`。 |

## PodChaos 示例

以下场景对标签为 `app.kubernetes.io/component=querynode` 的一个 Pod 注入 60 秒故障，等待期间可执行验证步骤。场景结束时会自动删除实验 CR；执行前请确认目标 Pod 所在节点已运行 `chaos-daemon`。

```json
{
  "ChaosMeshParams_0": {
    "operation": "create",
    "kind": "PodChaos",
    "instanceId": "in01-example",
    "duration": "60s",
    "labelSelectors": {
      "app.kubernetes.io/component": "querynode"
    },
    "attributes": {
      "action": "pod-failure",
      "mode": "one"
    }
  },
  "WaitParams_1": {
    "waitMinutes": 2
  }
}
```

> `attributes` 会直接合并到 Chaos Mesh `spec`。请按对应 Chaos Mesh CRD 的 schema 传递类型专属字段。
