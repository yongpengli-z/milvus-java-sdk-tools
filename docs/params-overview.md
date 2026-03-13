## milvus-java-sdk-toos 说明文档（面向“LLM 生成 JSON”）

### 1. 项目定位 / 你在用什么

本项目是一个 **“参数驱动”的 Milvus 测试/压测执行器**：

- **输入**：前端传入的 JSON（主要是 `customize_params`），以及少量启动参数（`uri/token/env/taskId/initial_params`）。
- **执行**：程序把 JSON **按规则解析并反序列化为 `custom.entity.*Params` 对象**，再由 `custom.common.ComponentSchedule` 调度到 `custom.components.*Comp` 执行，从而完成对 Milvus 的功能/性能测试。
- **Milvus SDK**：`pom.xml` 里使用 **`io.milvus:milvus-sdk-java`**（版本会随 Milvus 更新，详见 `pom.xml`），同时创建：
  - **V2 client**：`io.milvus.v2.client.MilvusClientV2`（大部分功能走它）
  - **V1 client**：`io.milvus.client.MilvusServiceClient`（segment info 等少数能力走它）

> 关键入口文件：
> - `custom/BaseTest.java`：主程序入口（`Main-Class`）
> - `custom/common/ComponentSchedule.java`：`customize_params` 的解析与执行编排

**项目目录结构**：

```
milvus-java-sdk-toos/
├── pom.xml                          # Maven 项目配置
├── README.md                        # 主要文档
├── src/
│   └── main/
│       ├── java/custom/
│       │   ├── BaseTest.java        # 主程序入口
│       │   ├── common/              # 公共组件
│       │   ├── components/          # 功能实现组件 (60个)
│       │   ├── config/              # 配置管理
│       │   ├── entity/              # 参数与结果实体 (65个 Params)
│       │   ├── pojo/                # 数据结构
│       │   └── utils/               # 工具类库
│       └── resources/
│           ├── log4j.properties     # 日志配置
│           └── example/             # 示例配置文件
├── ci/
│   ├── Build.groovy                 # Jenkins CI 构建配置
│   ├── docker/
│   │   └── Dockerfile               # Docker 镜像配置
│   ├── pod/
│   │   ├── build.yaml               # Kubernetes Pod 构建配置
│   │   ├── run-test.yaml            # 测试执行 Pod 配置
│   │   └── ali-create-instance.yaml # 阿里云实例创建配置
│   └── rbac/
│       └── milvus-deploy-rbac.yaml  # Milvus K8s 部署 RBAC 配置
├── azure-aks-helm/                  # 【新增】AKS Helm 部署方案
│   ├── README.md
│   ├── values.yaml
│   └── setup-aks-workload-identity.sh
└── azure-docker-compose/            # 【新增】Docker Compose 部署方案
    ├── README.md
    ├── docker-compose.yml
    └── milvus.yaml
```

---

### 2. 启动参数（System Properties）说明

主入口：`custom.BaseTest`（通过 JVM `-D` 传参，而不是 `args[]`）。

#### 2.0 两种使用方式：已有 Milvus vs 创建 Milvus 实例

本工具支持两种“获取 Milvus 实例”的方式（由你的场景决定）：

- **方式 A：已有 Milvus 实例（推荐给客户/本地）**
  - 你已经有可访问的 Milvus（standalone/cluster/cloud 实例皆可）。
  - **直接传 `-Duri`（可选 `-Dtoken`）**，然后在 `customize_params` 里编排 create collection / insert / search 等步骤即可。

- **方式 B：没有 Milvus 实例，需要先通过管控创建（cloud/内部环境可用）**
  - 你希望由工具通过内部服务创建实例，然后在同一次任务里继续跑 Milvus 测试。
  - 做法：在 `customize_params` 的第一个步骤放 **`CreateInstanceParams_0`**（见 `src/main/resources/example/createinstance.json`）。
  - **注意**：
    - 这条链路会调用 `custom.utils.*ServiceUtils` 的云服务/资源管理接口（例如 cloud-service / rm-service / ops-service），通常只在你们内部环境可用；普通客户如果没有这些服务权限，无法使用”创建实例”能力。
    - **可指定创建到哪个账号**：通过 `CreateInstanceParams` 的 **`accountEmail/accountPassword`** 传入目标账号；留空则会使用默认/临时账号登录（见 `CreateInstanceComp` 的账号检查逻辑）。
    - 当你不传 `-Duri` 时，`BaseTest` 启动阶段不会创建 `milvusClientV2/milvusClientV1`，所以 **`customize_params` 必须以 `CreateInstanceParams_0` 开头**，否则后续任何 Milvus 操作组件会因为 client 为空而失败。
    - `initial_params` 的 `cleanCollection` 只会在 `BaseTest` 启动时、且已连接到实例时执行；如果实例是在运行过程中通过 `CreateInstanceParams` 创建的，清理逻辑不会自动补跑。需要你在后续步骤里显式编排（例如 `DropCollectionParams` 清理、或直接创建新 collection）。

- **方式 C：没有 Milvus 实例，通过 Helm 部署（Kubernetes 环境）**
  - 你希望在 Kubernetes 集群中通过 Helm Chart 直接部署 Milvus，然后继续跑测试。
  - 做法：在 `customize_params` 的第一个步骤放 **`HelmCreateInstanceParams_0`**（见 5.7 节详细说明）。
  - **环境约束**：
    - **devops / fouram 环境**：**只能使用 Helm 部署**（不支持管控方式 B），必须使用 `HelmCreateInstanceParams`。
    - **其他环境**（如 awswest、alihz 等）：既可以通过管控部署（方式 B 的 `CreateInstanceParams`），也可以使用 Helm 部署（`HelmCreateInstanceParams`），两种方式均可。
  - **与管控部署的区别**：Helm 方式直接操作 Kubernetes 集群，不经过 cloud-service / rm-service 等管控链路，适用于自建 K8s 环境或需要更灵活配置的场景。

#### 2.1 必需参数

- **`-DtaskId`**：整型。`BaseTest` 中 `Integer.parseInt` 强依赖它（不传会直接报错）。
- **`-Duri`**：Milvus URI（例如 standalone 的 `http://localhost:19530`，或 cloud 的 https endpoint）。
- **`-Denv`**：环境标识，映射到 `custom.config.EnvEnum`（见下文“枚举字段”）。
- **`-Dinitial_params`**：JSON 字符串，反序列化到 `custom.entity.InitialParams`（至少传 `{}`，不建议空串）。
- **`-Dcustomize_params`**：JSON 字符串（本文重点），用于驱动组件执行。

#### 2.2 可选参数

- **`-Dtoken`**：Milvus token（不传时，`MilvusConnect.provideToken(uri)` 可能会尝试从内部 cloud-test API 获取 root 密码并拼 `root:<pwd>`；否则视为非 cloud 模式）。

---

### 3. JSON 输入总览：`initial_params` vs `customize_params`

#### 3.1 `initial_params`（初始化阶段）

对应类：`custom.entity.InitialParams`

- **`cleanCollection`**（boolean）：是否在启动时清空实例内所有 collection。

示例：

```json
{"cleanCollection": false}
```

对应执行：`custom.components.InitialComp.initialRunning()`。

#### 3.2 `customize_params`（主执行阶段）

对应类：由 `custom.common.ComponentSchedule.runningSchedule()` 解析。

它是一个 JSON object：每个 key 对应一个“步骤”，value 是该步骤的参数对象。

---

### 4. `customize_params` 的构造规则（最重要）

#### 4.1 顶层 Key 命名规则：`<ParamsClassName>_<序号>`

`ComponentSchedule` 会做两件事：

- **按序号排序执行**：它会把 key 按 `_` 后面的数字排序（越小越先执行）。
- **用反射加载参数类**：它会把 `_` 前面的部分当作类名，加载 `custom.entity.<ParamsClassName>`，并把 value 反序列化成该类对象。

因此顶层 key 必须严格满足：

- **必须包含 `_数字` 后缀**，例如：
  - `CreateCollectionParams_0`
  - `CreateIndexParams_1`
  - `InsertParams_2`
- **`ParamsClassName` 必须和 `custom.entity` 下的类名一致**（大小写敏感）。
- **序号必须是整数**（用于排序）。

#### 4.2 顶层 Value：参数对象

每个 step 的 value 是一个 JSON object（字段名对应 `*Params` 的字段名/属性名）。

**重要约束（来自当前代码实现）**：

- 很多组件代码没有做 `null` 保护，**List 字段建议永远显式给 `[]`**，不要省略，否则容易 `NullPointerException`。
  - 典型：`LoadParams.loadFields`、`SearchParams.outputs`、`QueryParams.outputs`、`QueryParams.ids`、`DeleteParams.ids` 等。
- 很多前端表单会用 **空字符串 `""` 作为“占位默认值”**（尤其是数字/枚举/数组输入框），但后端 `*Params` 字段通常是 `int/long/boolean/enum/List`：
  - **数字字段**：不要传 `""`，请传 `0` / `null` / 或直接不传（取决于字段是否必填）。
  - **枚举字段**（Milvus SDK enum）：不要传 `""`，请传正确的枚举常量名或 `null`/不传。
  - **List 字段**：不要传 `""`/`{}`，请传 `[]` 或 `["a","b"]`。
- **`CreateCollectionParams` 的 `numPartitions` 与 `partitionKey` 约束**：
  - 当 `numPartitions > 0` 时，`fieldParamsList` 中**必须至少有一个字段的 `partitionKey` 为 `true`**。
  - 当 `numPartitions = 0` 时，所有字段的 `partitionKey` 都应为 `false`。
  - 违反此约束会导致 Milvus 报错：`num_partitions should only be specified with partition key field enabled`。

#### 4.2.1 “前端默认值/必填”如何理解（LLM 生成 JSON 的关键）

本仓库的 `custom.entity/*Params.java` 字段注释已补充：

- **前端必填**：来自前端 `test-platform-web/src/views/run/customize/components/items/*Edit.vue` 的 `<el-form-item required>`（或 `required` 标记）。
- **前端默认值**：来自各 `*Edit.vue` 的 `data(){ ...Form: {...} }` 初始化对象。

注意：**后端不会自动帮你“填充前端默认值”**。如果你自己构造 JSON 省略字段：

- `int/long/boolean` 会变成 Java 默认值（0/false）
- `String/List/enum` 往往会是 `null`（很多组件未做 null 保护，会 NPE）

#### 4.3 全局状态（“省配置”的关键）

`BaseTest` 维护了一些全局变量用于默认行为，其中最关键的是：

- **`globalCollectionNames`**：已存在/新建的 collection 名列表。
  - 大多数组件如果 `collectionName` 为空，会默认使用 `globalCollectionNames` 的最后一个（即“最近创建/最近记录”的 collection）。
- **`insertCollectionIndex/searchCollectionIndex/queryCollectionIndex/upsertCollectionIndex`**：当 `collectionRule=sequence` 时用于轮转选择 collection。

因此，LLM 生成 JSON 时可以遵循：

- 如果用户没指定 collection：`"collectionName": ""`（或省略，但有些类/逻辑会 NPE，建议传空串）；
- 如果用户指定“随机选一个 collection”：`"collectionRule": "random"`；
- 如果用户指定“轮询所有 collection”：`"collectionRule": "sequence"`。

---

