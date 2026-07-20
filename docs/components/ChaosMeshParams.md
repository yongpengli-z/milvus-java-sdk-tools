# ChaosMeshParams

在有 Chaos Mesh CRD 的 Kubernetes 集群中创建或删除一次受限的故障实验。对应组件：`custom.components.ChaosMeshComp`。

组件使用当前 `env` 的 kubeconfig；也可以通过 JVM 参数 `-Dkubeconfig=/path/to/config` 覆盖。首次执行建议使用 `dryRun: true`，它只验证参数并返回将要创建的 Custom Resource，不会访问 Kubernetes API。

## 安全约束

- `create` 必须指定目标实例（`namespace`、`instanceId`，或前序步骤创建的全局实例）、正数 `duration` 和至少一个 `labelSelectors`。
- 未传 `namespace` 时，组件使用 `milvus-<instanceId>`；未传 `instanceId` 时，会使用前序 `CreateInstanceParams` 写入的全局 instance ID。
- 组件将 `spec.selector.namespaces` 固定为最终解析出的 namespace，不能跨 namespace 注入故障。
- 初始支持 `PodChaos`、`NetworkChaos`、`StressChaos`、`TimeChaos` 和 `IOChaos`。
- 测试场景必须在实验后追加 `delete` 步骤；删除 CR 后 Chaos Mesh 会停止故障注入。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `operation` | String | 否 | `create` | `create` 或 `delete`。 |
| `kind` | String | 是 | — | Chaos Mesh 类型：`PodChaos` / `NetworkChaos` / `StressChaos` / `TimeChaos` / `IOChaos`。 |
| `name` | String | 是 | — | Chaos Mesh Custom Resource 名称。 |
| `instanceId` | String | 否 | `""` | Milvus instance ID；未传 `namespace` 时自动转换为 `milvus-<instanceId>`。为空时回退到前序步骤创建的全局实例。 |
| `namespace` | String | 否 | `""` | CR 所在 namespace，同时也是被注入的目标 namespace。显式传入时优先级最高。 |
| `duration` | String | create 时是 | — | Go duration，例如 `60s`、`2m`、`1h30m`。 |
| `labelSelectors` | Map<String, String> | create 时是 | `{}` | 目标 Pod 标签；至少填写一项。 |
| `attributes` | Map<String, Object> | 否 | `{}` | 类型专属的 `spec` 字段，例如 PodChaos 的 `action`，NetworkChaos 的 `delay` / `loss`。不能覆盖组件生成的 `duration`、`selector`。 |
| `dryRun` | boolean | 否 | `false` | 为 `true` 时仅返回生成的资源体，不会修改集群。 |

## PodChaos 示例

以下场景先预览，再对标签为 `app.kubernetes.io/component=querynode` 的一个 Pod 注入 60 秒故障，最后显式删除实验。执行前请确认目标 Pod 所在节点已运行 `chaos-daemon`。

```json
{
  "ChaosMeshParams_0": {
    "operation": "create",
    "kind": "PodChaos",
    "name": "querynode-pod-failure-smoke",
    "instanceId": "in01-example",
    "duration": "60s",
    "labelSelectors": {
      "app.kubernetes.io/component": "querynode"
    },
    "attributes": {
      "action": "pod-failure",
      "mode": "one"
    },
    "dryRun": true
  },
  "ChaosMeshParams_1": {
    "operation": "create",
    "kind": "PodChaos",
    "name": "querynode-pod-failure-smoke",
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
  "WaitParams_2": {
    "waitMinutes": 2
  },
  "ChaosMeshParams_3": {
    "operation": "delete",
    "kind": "PodChaos",
    "name": "querynode-pod-failure-smoke",
    "instanceId": "in01-example"
  }
}
```

> `attributes` 会直接合并到 Chaos Mesh `spec`。请按对应 Chaos Mesh CRD 的 schema 传递类型专属字段。
