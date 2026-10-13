# CreateQueryClusterParams

Creates a VectorLake QueryCluster (`in07`). This component uses cloud-service
`POST /cloud/v1/vectorlake/query-cluster`; it does not use the Milvus
`classId` create path.

## 双通道（session token / apiKey）

- 注入账号**有密码**（能登录 cloud-service 拿到 session token）：走原
  cloud-service 通道，行为不变。
- 注入账号**无密码但有 apiKey**：自动切换到 OpenAPI 通道
  （`api.<domain>`，Bearer apiKey 鉴权，无需密码）：
  - 创建 `POST /v2/clusters/createOnDemandCluster`（`sessionTTL` 映射为 `autoSuspend`）
  - 详情/列表 `GET /v2/clusters/onDemandClusters[...]`
  - VectorLake 状态/开启 `GET|POST /v2/on-demand-compute[/enable]`（enable 响应不含
    VL id，组件用 RM 免密 list 按 `in06-` 前缀反查）
- 两通道都无可用凭证时报错。client 初始化的 token 优先用注入的 `apiKey`，
  其次才是 `listManagedApiKeys`（仅 token 通道可用）。

## Parameters

| Field | Type | Required | Default | Description |
|------|------|:----:|--------|------|
| `clusterName` | String | Yes | `""` | QueryCluster display name |
| `cuSize` | int | Yes | `8` | QueryCluster CU, must be `>= 8` |
| `projectId` | String | No | 注入账号的 projectId | Target project ID（一般不填，默认创建到提交人专属 project） |
| `projectName` | String | No | `""` | Used only when `projectId` is empty |
| `regionId` | String | No | 任务 env 的 regionId | Target region（一般不填） |
| `sessionTTL` | String | No | `30m` | VectorLake session TTL if a new `in06` is created |
| `useHours` | int | No | `24` | 实例保留小时数（QTP 到期清理用，组件本身不消费）；0 = 任务跑完即删 |
| `maxQueryNodeCU` | Integer | No | backend default | VectorLake capacity setting if a new `in06` is created（仅 JSON 配置，不在前端表单） |
| `maxQueryNodeReplicas` | Integer | No | backend default | VectorLake capacity setting if a new `in06` is created（仅 JSON 配置，不在前端表单） |
| `vectorLakeDbVersion` | String | No | `""` | Expected `in06` version. If set, the component creates `in06` when absent and upgrades it before creating `in07` |
| `queryClusterDbVersion` | String | No | `""` | Optional `in07` QueryNode version to apply after creation |

## Notes

- `in07` uses `cuSize`, not `class-1-enterprise` / `classId`.
- QC 创建成功的稳态是 **SUSPENDED**（QN Deployment `replicas=0`，scale-to-zero），
  不是 RUNNING；首次带 `cluster_id` 的查询打到 kite-coordinator 才会冷启动拉起 QN。
  注意 cloud-service 通道会把 SUSPENDED 翻译成 **STOPPED** 返回
  （QueryClusterStatusDisplayMapper），OpenAPI 通道保留 SUSPENDED。
  组件等待 RUNNING / SUSPENDED / STOPPED 均视为创建完成。
- `vectorLakeDbVersion` and `queryClusterDbVersion` are different image
  streams. `vectorLakeDbVersion` is resolved with `ins_type=6`; 
  `queryClusterDbVersion` is resolved with `ins_type=7`.
- cloud-service cannot pin the `in06` image when it implicitly creates VectorLake.
  If `vectorLakeDbVersion` is set, the component creates `in06` when absent,
  waits for RUNNING, upgrades it, then creates `in07`. If `in06` already exists,
  the component upgrades it directly before creating `in07`.
- cloud-service also cannot pin the `in07` image at create time. Use
  `queryClusterDbVersion` to upgrade after create.
- The component always initializes clients after creation by resolving the
  injected account's personal API key (`cloudAccount.apiKey` first, then the
  managed-key list) and using the QueryCluster endpoint returned by
  cloud-service / OpenAPI.

## Example

```json
{
  "CreateQueryClusterParams_0": {
    "clusterName": "qc-test",
    "cuSize": 8,
    "sessionTTL": "30m",
    "vectorLakeDbVersion": "vectorlake-20260509-xxxxxxx",
    "queryClusterDbVersion": "querycluster-20260509-yyyyyyy"
  }
}
```

`projectId` / `regionId` 默认分别取注入账号的专属 project 和任务 env，
一般无需填写；需要指定其他 project/region 时才显式给出。
