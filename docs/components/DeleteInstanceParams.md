# DeleteInstanceParams

删除 Milvus 实例。对应组件：`custom.components.DeleteInstanceComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `useCloudTestApi` | boolean | 是 | `false` | 是否使用 Cloud-Test API 删除实例 |
| `instanceId` | String | 否 | `""` | 留空使用当前实例；可传普通实例 ID、Global Cluster ID、primary ID 或 secondary ID |

## Global Cluster 删除行为

组件不会要求额外参数，只根据 `instanceId` 判断删除范围：

- 普通实例 ID：调用 RM `/resource/v1/instance/milvus/delete` 删除该实例。
- **QueryCluster ID（`in07-` 前缀）**：走专属删除通道——有 session token 调
  cloud-service `DELETE /cloud/v1/vectorlake/query-cluster/{id}`，无密码账号（有注入
  apiKey）走 OpenAPI `DELETE /v2/clusters/onDemandClusters/{id}`。in07 不能走 RM 通用删除
  （activity-provider 无 ins_type=7 分发，NPE 假成功）。VL(in06) 是 (project, region)
  共享底座，**不随 QC 删除**，生命周期由平台管理。
- secondary ID：只调用 RM `/resource/v1/global_cluster/milvus/delete_secondary` 删除该 secondary。
- Global Cluster ID：删除全部 secondary 后调用 RM `/resource/v1/global_cluster/milvus/disband`，保留 primary。
- primary ID：删除全部 secondary，disband Global Cluster，再调用 RM `/resource/v1/instance/milvus/delete` 删除 primary。

当 `useCloudTestApi=true` 时：

- 普通实例 ID：调用 cloud-test `/cloud/v1/test/deleteInstance`。
- secondary ID：调用 cloud-test `/cloud/v1/test/deleteInstance`，由 Cloud-Test 从 meta 判断 role 并删除 secondary。
- Global Cluster ID / primary ID：secondary 和 primary 删除走 cloud-test；disband 暂未找到 cloud-test 对应接口，仍调用 RM `/resource/v1/global_cluster/milvus/disband`。
- **cloud-test 回退**：cloud-test 响应为空/非 JSON（如 HTTP 500）时自动回退 RM `/resource/v1/instance/milvus/delete`（cloud-test 对提交人个人 project 下的实例会 500，2026-10 起实例创建在提交人专属 project）。

删除后确认（waitForInstancesGone）走 RM `describeInstance`（身份无关），不再用 cloud-service
`listInstance`（按 project 过滤，个人 project 场景会漏判）。删除响应为失败时会先确认实例
是否已实际消失，已消失按成功处理。

## JSON 示例

```json
{"DeleteInstanceParams_0": {"useCloudTestApi": false}}
```
