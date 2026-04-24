# CreateInstanceParams

创建 Zilliz Cloud Milvus 实例。对应组件：`custom.components.CreateInstanceComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `instanceName` | String | 是 | `""` | 不能与已有实例重名 |
| `dbVersion` | String | 是 | `latest-release` | 镜像版本（见特殊值说明） |
| `cuType` | String | 否 | `class-1-enterprise` | CU 规格（见规格说明） |
| `architecture` | int | 否 | `2` | `1`=AMD(x86)，`2`=ARM |
| `instanceType` | int | 推荐必传 | `1` | 不传时默认 0 可能异常，建议传 `1` |
| `replica` | int | 是 | `1` | 副本数。>1 时 CU 必须 ≥8 |
| `rootPassword` | String | 是 | `Milvus123` | root/db_admin 密码 |
| `roleUse` | String | 是 | `root` | 连接角色：`root` 或 `db_admin` |
| `useHours` | int | 是 | `10` | 使用时长（小时） |
| `accountEmail` | String | 否 | `""` | 留空使用默认账号 |
| `accountPassword` | String | 否 | `""` | |
| `bizCritical` | boolean | 否 | `false` | 是否重保 |
| `monopolized` | boolean | 否 | `false` | 是否独占模式 |
| `qnBreakUp` | boolean | 否 | `false` | 是否打散 QN |
| `kmsIntegrationId` | String | 否 | `""` | CMEK 集成 ID，留空不使用 |
| `streamingNodeParams` | Object | 否 | `null` | **已废弃**，可省略 |

## cuType 规格

| 类型 | 格式 | 说明 |
|------|------|------|
| Memory（内存型） | `class-{N}-enterprise` | 标准，适用大部分场景 |
| DiskANN（磁盘型） | `class-{N}-disk-enterprise` | 大数据量低成本 |
| Tiered（分层存储） | `class-{N}-tiered-enterprise` | 冷热数据自动分层 |

`{N}` 可选：`1`/`2`/`4`/`6`/`8`/`12`/`16`/`20`/`24`/`28`/`32`/`64`/`128`

## dbVersion 特殊值

- `latest-release`：最新 release 版本
- `nightly`：最新 nightly 版本
- 其他值：按关键字查询匹配镜像

## ⚠️ 连接 token 格式

创建完成后连接 Milvus 的 token：**`db_admin:<rootPassword>`**

> **不是** `root:<rootPassword>`，即便 `roleUse=root`。底层账号是 `db_admin`。
> 例：`rootPassword=Milvus123` → token 为 `db_admin:Milvus123`

## 约束

- `replica > 1` 时 CU 必须 `≥ 8`
- 创建时先用 `replica=1`，成功后通过 ScaleInstanceParams 调整
- `bizCritical`/`monopolized` 结果字段反映 API 调用是否成功，而非参数值

## JSON 示例

```json
{
  "CreateInstanceParams_0": {
    "dbVersion": "latest-release",
    "cuType": "class-8-enterprise",
    "instanceName": "my-test",
    "instanceType": 1,
    "replica": 1,
    "rootPassword": "Milvus123",
    "roleUse": "root",
    "useHours": 10
  }
}
```
