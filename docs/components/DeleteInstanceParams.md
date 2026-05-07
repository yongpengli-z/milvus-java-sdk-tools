# DeleteInstanceParams

删除 Milvus 实例。对应组件：`custom.components.DeleteInstanceComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `useCloudTestApi` | boolean | 是 | `false` | 是否使用 Cloud-Test API 删除实例 |
| `instanceId` | String | 否 | `""` | 留空使用当前实例；可传普通实例 ID、Global Cluster ID、primary ID 或 secondary ID |
| `accountEmail` | String | 否 | `""` | |
| `accountPassword` | String | 否 | `""` | |

## Global Cluster 删除行为

组件不会要求额外参数，只根据 `instanceId` 判断删除范围：

- 普通实例 ID：调用 RM `/resource/v1/instance/milvus/delete` 删除该实例。
- secondary ID：只调用 RM `/resource/v1/global_cluster/milvus/delete_secondary` 删除该 secondary。
- Global Cluster ID：删除全部 secondary 后调用 RM `/resource/v1/global_cluster/milvus/disband`，保留 primary。
- primary ID：删除全部 secondary，disband Global Cluster，再调用 RM `/resource/v1/instance/milvus/delete` 删除 primary。

当 `useCloudTestApi=true` 时：

- 普通实例 ID：调用 cloud-test `/cloud/v1/test/deleteInstance`。
- secondary ID：调用 cloud-test `/cloud/v1/test/deleteInstance`，由 Cloud-Test 从 meta 判断 role 并删除 secondary。
- Global Cluster ID / primary ID：secondary 和 primary 删除走 cloud-test；disband 暂未找到 cloud-test 对应接口，仍调用 RM `/resource/v1/global_cluster/milvus/disband`。

## JSON 示例

```json
{"DeleteInstanceParams_0": {"useCloudTestApi": false}}
```
