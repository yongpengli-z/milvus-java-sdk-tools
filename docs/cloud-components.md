#### 5.6 Cloud/运维相关（如果你的客户只关心 Milvus 功能测试，可忽略）

这些组件主要通过 `custom.utils.*ServiceUtils` 调用内部服务（cloud-service / rm-service / ops-service）。字段含义以对应 `*Params` 类为准：

- `CreateInstanceParams`（`CreateInstanceComp`）
- `DeleteInstanceParams`（`DeleteInstanceComp`）
- `StopInstanceParams`（`StopInstanceComp`）
- `ResumeInstanceParams`（`ResumeInstanceComp`）
- `RestartInstanceParams`（`RestartInstanceComp`）
- `RollingUpgradeParams`（`RollingUpgradeComp`）
- `ModifyParams`（`ModifyParamsComp`）
- `ScaleInstanceParams`（`ScaleInstanceComp`）
- `UpdateIndexPoolParams`（`UpdateIndexPoolComp`）
- `AlterInstanceIndexClusterParams`（`AlterInstanceIndexClusterComp`）
- `RestoreBackupParams`（`RestoreBackupComp`）

前端默认值/必填速查（来自 `*Edit.vue`，详细字段见 `custom.entity/*Params` 的 Javadoc）：

- **`CreateInstanceParams`**（`CreateInstanceComp`，前端 `createInstanceEdit.vue`）

  | 字段 | 类型 | 必填 | 默认值 | 说明 |
  |------|------|:----:|--------|------|
  | `instanceName` | String | 是 | `""` | 实例名称，不能与已有实例重名 |
  | `dbVersion` | String | 是 | `latest-release` | 镜像版本（见下方特殊值说明） |
  | `cuType` | String | 否 | `class-1-enterprise` | CU 规格（见下方说明） |
  | `architecture` | int | 否 | `2` | 架构：`1`=AMD（x86），`2`=ARM |
  | `instanceType` | int | 否 | `1` | 实例类型（通常保持默认） |
  | `replica` | int | 是 | `1` | 副本数。`>1` 时 CU 必须 ≥8；创建时先用 replica=1，成功后通过 modifyInstance 设置 |
  | `rootPassword` | String | 是 | `Milvus123` | root/db_admin 密码，用于后续连接 token 生成 |
  | `roleUse` | String | 是 | `root` | 创建完成后使用哪个角色连接：`root` 或 `db_admin` |
  | `useHours` | int | 是 | `10` | 实例使用时长（小时），用于生命周期管理 |
  | `accountEmail` | String | 否 | `""` | 创建实例所用账号邮箱，留空使用默认账号 |
  | `accountPassword` | String | 否 | `""` | 创建实例所用账号密码 |
  | `bizCritical` | boolean | 否 | `false` | 是否重保，创建后调用 RM `update_biz_critical`。**结果说明**：返回值反映 API 调用是否成功，而非参数值 |
  | `monopolized` | boolean | 否 | `false` | 是否独占模式，创建后调用 RM `update_monopolized`。**结果说明**：返回值反映 API 调用是否成功，而非参数值 |
  | `qnBreakUp` | boolean | 否 | `false` | 是否打散 QN（Query Node 分散部署到不同机器），创建后调用 RM `update_qn_break_up` |
  | `kmsIntegrationId` | String | 否 | `""` | CMEK 集成 ID，通过 cloud-meta `/cmek/add` 创建获取。传入后关联 KMS 加密密钥 |
  | `streamingNodeParams` | Object | 否 | `null` | **已废弃**，可省略或传 `null` |

  - **`cuType` 实例规格说明**：
    前端使用二级级联选择器，3 大类型：

    | 类型 | cuType 格式 | 说明 |
    |------|------------|------|
    | **Memory**（内存型） | `class-{N}-enterprise` | 标准内存型实例，适用于大部分场景 |
    | **DiskANN**（磁盘型） | `class-{N}-disk-enterprise` | 基于 DiskANN 索引的磁盘型实例，适合大数据量低成本场景 |
    | **Tiered**（分层存储） | `class-{N}-tiered-enterprise` | 分层存储实例，冷热数据自动分层 |

    其中 `{N}` 为 CU 数量，可选值：`1`（standalone）、`2`、`4`、`6`、`8`、`12`（cluster 起）、`16`、`20`、`24`、`28`、`32`、`64`、`128`

    示例：
    - 4CU Memory 实例：`"cuType": "class-4-enterprise"`
    - 4CU DiskANN 实例：`"cuType": "class-4-disk-enterprise"`
    - 4CU Tiered 实例：`"cuType": "class-4-tiered-enterprise"`
    - 12CU Memory 集群：`"cuType": "class-12-enterprise"`

  - **`dbVersion` 特殊值说明**：
    - `latest-release`：后端会获取最新的 release 版本镜像
    - `nightly`：后端会自动查找最新的 nightly 版本镜像（通过关键字查询匹配的镜像，返回最新的一个）
    - 其他值：后端会通过关键字查询匹配的镜像，并返回最新的一个
  - **`qnBreakUp`**：是否打散 QN（Query Node 分散部署到不同机器）。设为 `true` 后，创建实例成功后会调用 RM 的 `update_qn_break_up` 接口。默认 `false`
  - **`kmsIntegrationId`**：CMEK（Customer Managed Encryption Key）集成 ID。通过 cloud-meta 的 `cmek/add` 接口创建后获取。传入后创建实例时会同时设置 `enableCMEK=true` 并关联 KMS 加密密钥。留空则不使用 CMEK
  - **`bizCritical`**（boolean）：是否将实例标记为业务关键（biz critical）。设为 `true` 后，创建实例成功后会调用 RM 的 `update_biz_critical` 接口（包含 `nodeCategories` 参数）。默认 `false`。**结果说明**：结果中的 `bizCritical` 字段反映的是 `update_biz_critical` API 调用是否成功，而非简单的参数值
  - **`monopolized`**（boolean）：是否将实例设为独占模式（monopolized）。设为 `true` 后，创建实例成功后会调用 RM 的 `update_monopolized` 接口。默认 `false`。**结果说明**：结果中的 `monopolized` 字段反映的是 `update_monopolized` API 调用是否成功，而非简单的参数值
  - **`streamingNodeParams`**（已废弃，不再需要）：创建实例时不再需要配置 streaming node 参数，可以省略该字段或传 `null`
- **`DeleteInstanceParams`**（`deleteInstanceEdit.vue`）
  - 必填：`useCloudTestApi`（注意后端字段名是 `useOPSTestApi`，需要映射/改 key）
  - 默认：`useCloudTestApi=false`，`instanceId/accountEmail/accountPassword=""`
- **`StopInstanceParams`**（`stopInstanceEdit.vue`）
  - 功能：停止 Milvus 实例
  - 默认：`instanceId=""`（留空则使用当前已创建的实例），`accountEmail=""`，`accountPassword=""`
- **`ResumeInstanceParams`**（`resumeInstanceEdit.vue`）
  - 功能：恢复已停止的 Milvus 实例
  - 默认：`instanceId=""`（留空则使用当前已创建的实例），`accountEmail=""`，`accountPassword=""`
- **`RestartInstanceParams`**（`restartInstanceEdit.vue`）
  - 功能：重启 Milvus 实例
  - 默认：`instanceId=""`（留空则使用当前已创建的实例），`accountEmail=""`，`accountPassword=""`
- **`RollingUpgradeParams`**（`rollingUpdateEdit.vue`）
  - 必填：`targetDbVersion`
  - 默认：`targetDbVersion=""`，`forceRestart=true`
- **`ModifyParams`**（`modifyParamsEdit.vue`）
  - 必填：`paramsList`
  - 默认：`needRestart=true`，`paramsList=[{paramName:"", paramValue:""}]`
- **`ScaleInstanceParams`**（`scaleInstanceEdit.vue`）
  - 功能：升降配 Milvus 实例的 CU 规格和/或副本数（replica）
  - 默认：`instanceId=""`（留空则使用当前已创建的实例），`targetCuType=""`，`targetReplica=1`，`accountEmail=""`，`accountPassword=""`
  - **`targetCuType`**：目标 CU 类型（classId），例如 `class-8-enterprise`。前端提供 Memory / DiskANN / Tiered 三级级联选择。留空表示不修改 CU
  - **`targetReplica`**：目标副本数。0 表示不修改 replica
  - **classId 与 replica 的关系**：replica 信息编码在 classId 中。replica=1 时省略（如 `class-8-enterprise`），replica>=2 时插在 CU 数后面（如 `class-8-2-enterprise`、`class-8-3-disk-enterprise`）
  - **约束**：replica > 1 时，CU 必须 >= 8
  - **操作顺序**：当 CU 和 replica 同时变更时（RM 不允许同时修改两者），后端会自动决定操作顺序：
    - 升配（CU 变大）：先升 CU → 再加 replica
    - 降配（CU 变小）：先减 replica → 再降 CU
    - 确保中间状态也满足 replica>1 时 CU>=8 的约束
  - 示例：

    ```json
    {
      "ScaleInstanceParams_0": {
        "instanceId": "",
        "targetCuType": "class-8-enterprise",
        "targetReplica": 2,
        "accountEmail": "user@example.com",
        "accountPassword": "password"
      }
    }
    ```

    上述示例会将实例升配到 8CU / 2 replica（最终 classId 为 `class-8-2-enterprise`）
- **`UpdateIndexPoolParams`**（`updateIndexPoolEdit.vue`）
  - 默认：`managerImageTag=""`，`workerImageTag=""`，`indexClusterId=""`（占位；后端是 int）
- **`AlterInstanceIndexClusterParams`**（`alterInstanceIndexClusterEdit.vue`）
  - 默认：`instanceId=""`，`indexClusterId=""`（占位；后端是 int）
- **`RestoreBackupParams`**（`restoreBackupEdit.vue`）
  - 必填：`backupId`、`restorePolicy`
  - 默认：`notChangeStatus=false`，`restorePolicy=1`，`skipCreateCollection=false`，`toInstanceId=""`，`truncateBinlogByTs=false`，`withRBAC=false`

---

#### 5.7 Helm 部署/删除 Milvus 实例（Kubernetes 环境）

这些组件用于在 Kubernetes 环境中通过 Helm Chart 部署和管理 Milvus 实例。与管控方式（`CreateInstanceParams`）不同，Helm 方式直接操作 Kubernetes 集群，不经过 cloud-service / rm-service 等管控链路。

**环境与部署方式对照**：

| 环境 | 管控部署（`CreateInstanceParams`） | Helm 部署（`HelmCreateInstanceParams`） |
|------|:---:|:---:|
| **devops / fouram** | 不支持 | 仅支持此方式 |
| **其他环境**（awswest、alihz 等） | 支持 | 支持 |

- `HelmCreateInstanceParams`（`HelmCreateInstanceComp`）：通过 Helm 部署 Milvus 实例
- `HelmDeleteInstanceParams`（`HelmDeleteInstanceComp`）：卸载 Helm 部署的 Milvus 实例

