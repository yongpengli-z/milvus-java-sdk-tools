## milvus-java-sdk-toos 说明文档（面向“LLM 生成 JSON”）

### 1. 项目定位 / 你在用什么

本项目是一个 **“参数驱动”的 Milvus 测试/压测执行器**：

- **输入**：前端传入的 JSON（主要是 `customize_params`），以及少量启动参数（`uri/token/env/taskId/initial_params`）。
- **执行**：程序把 JSON **按规则解析并反序列化为 `custom.entity.*Params` 对象**，再由 `custom.common.ComponentSchedule` 调度到 `custom.components.*Comp` 执行，从而完成对 Milvus 的功能/性能测试。
- **Milvus SDK**：`pom.xml` 里固定使用 **`io.milvus:milvus-sdk-java:2.6.6`**，同时创建：
  - **V2 client**：`io.milvus.v2.client.MilvusClientV2`（大部分功能走它）
  - **V1 client**：`io.milvus.client.MilvusServiceClient`（segment info 等少数能力走它）

> 关键入口文件：
> - `custom/BaseTest.java`：主程序入口（`Main-Class`）
> - `custom/common/ComponentSchedule.java`：`customize_params` 的解析与执行编排

---

### 2. 启动参数（System Properties）说明

主入口：`custom.BaseTest`（通过 JVM `-D` 传参，而不是 `args[]`）。

#### 2.0 两种使用方式：已有 Milvus vs 创建 Milvus 实例

本工具支持两种“获取 Milvus 实例”的方式（由你的场景决定）：

- **方式 A：已有 Milvus 实例（推荐给客户/本地）**
  - 你已经有可访问的 Milvus（standalone/cluster/cloud 实例皆可）。
  - **直接传 `-Duri`（可选 `-Dtoken`）**，然后在 `customize_params` 里编排 create collection / insert / search 等步骤即可。

- **方式 B：没有 Milvus 实例，需要先创建（仅 cloud/内部环境可用）**
  - 你希望由工具通过内部服务创建实例，然后在同一次任务里继续跑 Milvus 测试。
  - 做法：在 `customize_params` 的第一个步骤放 **`CreateInstanceParams_0`**（见 `src/main/resources/example/createinstance.json`）。
  - **注意**：
    - 这条链路会调用 `custom.utils.*ServiceUtils` 的云服务/资源管理接口（例如 cloud-service / rm-service / ops-service），通常只在你们内部环境可用；普通客户如果没有这些服务权限，无法使用“创建实例”能力。
    - **可指定创建到哪个账号**：通过 `CreateInstanceParams` 的 **`accountEmail/accountPassword`** 传入目标账号；留空则会使用默认/临时账号登录（见 `CreateInstanceComp` 的账号检查逻辑）。
    - 当你不传 `-Duri` 时，`BaseTest` 启动阶段不会创建 `milvusClientV2/milvusClientV1`，所以 **`customize_params` 必须以 `CreateInstanceParams_0` 开头**，否则后续任何 Milvus 操作组件会因为 client 为空而失败。
    - `initial_params` 的 `cleanCollection` 只会在 `BaseTest` 启动时、且已连接到实例时执行；如果实例是在运行过程中通过 `CreateInstanceParams` 创建的，清理逻辑不会自动补跑。需要你在后续步骤里显式编排（例如 `DropCollectionParams` 清理、或直接创建新 collection）。

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

### 5. 支持的组件（= 可用的 `*Params` 列表）

最终能跑的 step 以 `ComponentSchedule.callComponentSchedule()` 的 `instanceof` 分发为准。当前支持 **38 种**：

#### 5.1 Milvus 核心链路（最常用）

##### 5.1.1 创建 Collection：`CreateCollectionParams`

对应组件：`custom.components.CreateCollectionComp`

字段（`custom.entity.CreateCollectionParams`）：

- **`collectionName`**（string，可空）：不传/空则自动生成随机名。前端默认：`""`。
- **`shardNum`**（int，前端必填）：前端默认：`1`。
- **`numPartitions`**（int，前端必填）：前端默认：`0`。
- **`enableDynamic`**（boolean，前端必填）：是否开启动态列。前端默认：`false`。
- **`fieldParamsList`**（list，前端必填）：字段定义（见 `FieldParams`）。前端默认：2 个字段（`Int64_0` 作为 PK + `FloatVector_1` 向量字段）。
- **`functionParams`**（object，可空）：function（例如 BM25）配置（见 `FunctionParams`）。前端默认：`{functionType:"", name:"", inputFieldNames:[], outputFieldNames:[]}`（注意 `functionType=""` 是占位，建议用 `null`/不传）。
- **`properties`**（list，前端必填）：collection properties（key/value）。前端默认：`[{propertyKey:"", propertyValue:""}]`（占位；不需要可传 `[]`）。
- **`databaseName`**（string，可空）：前端默认：`""`。

`fieldParamsList` 的元素类型：`custom.entity.FieldParams`

- **`fieldName`**（string）：前端默认模板里是 `Int64_0` / `FloatVector_1`；新增行默认 `""`。
- **`dataType`**（enum 字符串，见下文“DataType 枚举”）：前端默认模板里是 `Int64` / `FloatVector`；新增行默认 `""`（占位，建议不要传空串）。
- **`primaryKey`**（boolean）：注意：Java 字段名是 `isPrimaryKey`，但示例/fastjson 常用 key 为 `primaryKey`。前端默认模板里首字段为 `true`。
- **`autoId`**（boolean）：仅主键字段可用。前端默认：`false`。
- **`dim`**（int）：向量维度（vector 类型必填）。前端默认模板里向量字段为 `768`。
- **`maxLength`**（int）：VarChar 必填。前端新增行默认 `null`（占位）；生成 JSON 时请传数字或不传（不要传 `""`）。
- **`maxCapacity`**（int）：Array 必填。前端新增行默认 `null`（占位）；生成 JSON 时请传数字或不传（不要传 `""`）。
- **`elementType`**（enum）：Array 元素类型。前端新增行默认 `null`（占位）；生成 JSON 时请传枚举名或 `null`/不传（不要传 `""`/`0` 占位）。
- **`partitionKey`**（boolean）：前端默认：`false`。
- **`nullable`**（boolean）：前端默认：`false`（且主键/向量字段会禁用 nullable=true）。
- **`enableMatch`**（boolean）：前端默认：`false`。
- **`enableAnalyzer`**（boolean）：前端默认：`false`。
- **`analyzerParamsList`**（list，可空）：前端新增行默认 `[{paramsKey:"", paramsValue:""}]`（占位；不需要可传 `[]`）。

##### 5.1.2 建索引：`CreateIndexParams`

对应组件：`custom.components.CreateIndexComp`

字段（`custom.entity.CreateIndexParams`）：

- **`collectionName`**（string，可空）：默认最后一个 collection
- **`databaseName`**（string，可空）
- **`indexParams`**（list，建议必填，可为 `[]`）

`indexParams` 的元素类型：`custom.entity.IndexParams`

- **`fieldName`**（string）：要建索引的字段名（注意不是 `filedName`）
- **`indextype`**（enum 字符串）：`io.milvus.v2.common.IndexParam.IndexType`
  - 注意：前端 `createIndexEdit.vue` 的 key 是 `indexType`，但后端字段名是 `indextype`；**请确保最终 JSON 使用 `indextype`**（或在你的参数生成/映射层做转换），否则会导致索引类型为空。
- **`metricType`**（enum 字符串，可空）：`io.milvus.v2.common.IndexParam.MetricType`
- **`jsonPath/jsonCastType`**（string，可空）：用于 JSON index
- **`buildLevel`**（string，可空）：例如 HNSW build level（仅部分向量类型使用）

> 重要：如果你想“让系统自动给所有向量字段建索引”，请传 `indexParams: []`（不要省略该字段）。

##### 5.1.3 Load：`LoadParams`

对应组件：`custom.components.LoadCollectionComp`

字段：

- **`loadAll`**（boolean，前端必填）：true 则加载实例内所有 collection。前端默认 `false`。
- **`collectionName`**（string，可空）：`loadAll=false` 时使用。前端默认 `""`。
- **`loadFields`**（list，建议必填）：不加载全部字段时指定字段名列表；不指定请传 `[]`。前端默认 `[]`（页面用 `tempLoadFields` 字符串输入再 split）。
- **`skipLoadDynamicField`**（boolean，前端必填）：前端默认 `false`。

##### 5.1.4 Insert：`InsertParams`

对应组件：`custom.components.InsertComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`partitionName`**（string，可空）：前端默认 `""`。
- **`startId`**（long，前端必填）：前端默认 `0`。
- **`numEntries`**（long，前端必填）：总写入量（内部会按 `batchSize` 切批）。前端默认 `1500000`。
- **`batchSize`**（long，前端必填）：前端默认 `1000`。
- **`numConcurrency`**（int，前端必填）：并发线程数。前端默认 `1`。
- **`dataset`**（string，前端必填）：`random/gist/deep/sift/laion`。前端默认 `random`。
- **`runningMinutes`**（long，前端必填）：Insert 中该字段>0 时会成为“时间上限”，否则以数据量批次数为准。前端默认 `0`。
- **`retryAfterDeny`**（boolean，可空）：禁写后是否等待重试。前端默认 `false`。
- **`ignoreError`**（boolean，可空）：出错是否忽略继续。前端默认 `false`。
- **`generalDataRoleList`**（list，可空）：数据生成规则（见 `GeneralDataRole`）。前端默认是“带 1 条空规则”的占位数组；**如果你不使用该能力，建议直接传 `[]`**。

##### 5.1.5 Search：`SearchParams`

对应组件：`custom.components.SearchComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`annsField`**（string，前端必填 & 强烈建议显式指定）：向量字段名。前端默认 `vectorField_1`（注意：`createCollectionEdit.vue` 默认向量字段名是 `FloatVector_1`，两者模板不强绑定，实际以你的向量字段名为准）。
- **`nq`**（int，前端必填）：query vectors 数量。前端默认 `1`。
- **`topK`**（int，前端必填）：前端默认 `1`。
- **`outputs`**（list，建议必填）：输出字段。前端默认 `[]`；不需要输出请传 `[]`。
- **`filter`**（string，可空）：Milvus expr（可包含 `$fieldName` 占位符，见下文）。前端默认 `""`。
- **`numConcurrency`**（int，前端必填）：前端默认 `10`。
- **`runningMinutes`**（long，前端必填）：Search 是纯按时间循环。前端默认 `10`。
- **`randomVector`**（boolean，前端必填）：前端默认 `true`。
- **`searchLevel`**（int，可空）：前端默认 `1`。
- **`indexAlgo`**（string，可空）：前端默认 `""`。
- **`targetQps`**（double，可空）：前端默认 `0`（0=不限制）。
- **`generalFilterRoleList`**（list，可空）：前端默认是“带 1 条空规则”的占位数组；**如果你不使用该能力，建议直接传 `[]`**。
- **`ignoreError`**（boolean，可空）：前端默认 `false`。

##### 5.1.6 Query：`QueryParams`

对应组件：`custom.components.QueryComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`outputs`**（list，建议必填）：前端默认 `[]`。
- **`filter`**（string，可空；支持 `$fieldName` 占位符）：前端默认 `""`。
- **`ids`**（list，建议必填）：不按 id 查询请传 `[]`。前端默认 `[]`（但页面用 `tempIDs` 字符串输入再 split）。
- **`partitionNames`**（list，可空）：前端初始模板里是 `""`（占位，失焦后会 split 成数组）；**建议生成 JSON 时直接用 `[]` 或 `["p1","p2"]`**。
- **`limit`**（long，可空）：前端初始模板里是 `""`（占位）；**建议生成 JSON 时传数字或不传**。
- **`offset`**（long，前端必填）：前端默认 `0`。
- **`numConcurrency`**（int，前端必填）：前端默认 `10`。
- **`runningMinutes`**（long，前端必填）：前端默认 `10`。
- **`targetQps`**（double，可空）：前端默认 `0`。
- **`generalFilterRoleList`**（list，可空）：前端默认是“带 1 条空规则”的占位数组；不使用建议传 `[]`。

##### 5.1.7 Upsert：`UpsertParams`

对应组件：`custom.components.UpsertComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`partitionName`**（string，可空）：前端默认 `""`。
- **`startId`**（long，前端必填）：前端默认 `0`。
- **`numEntries`**（long，前端必填）：前端默认 `1500000`。
- **`batchSize`**（long，前端必填）：前端默认 `1000`。
- **`numConcurrency`**（int，前端必填）：前端默认 `1`。
- **`dataset`**（string，前端必填）：前端默认 `random`。
- **`runningMinutes`**（long，可空）：>0 时作为时间上限。前端模板里存在该字段且默认 `0`（UI 未展示该输入项）。
- **`retryAfterDeny`**（boolean，可空）：前端默认 `false`。
- **`generalDataRoleList`**（list，可空）：前端默认是“带 1 条空规则”的占位数组；不使用建议传 `[]`。
- **`targetQps`**（int，可空）：前端默认 `0`。

##### 5.1.8 Delete：`DeleteParams`

对应组件：`custom.components.DeleteComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`partitionName`**（string，可空）：前端默认 `""`。
- **`ids`**（list，建议必填）：不按 id 删请传 `[]`。前端默认 `[]`（页面用 `tempIDs` 字符串输入再 split）。
- **`filter`**（string，可空）：前端默认 `""`。

##### 5.1.9 Flush：`FlushParams`

对应组件：`custom.components.FlushComp`

字段：

- **`flushAll`**（boolean，前端必填）：前端默认 `false`。
- **`collectionName`**（string，可空）：前端默认 `""`。

##### 5.1.10 Release：`ReleaseParams`

对应组件：`custom.components.ReleaseCollectionComp`

字段：

- **`releaseAll`**（boolean，前端必填）：前端默认 `false`。
- **`collectionName`**（string，可空）：前端默认 `""`。

##### 5.1.11 Drop Collection：`DropCollectionParams`

对应组件：`custom.components.DropCollectionComp`

字段：

- **`dropAll`**（boolean，前端必填）：前端默认 `false`。
- **`collectionName`**（string，可空）：前端默认 `""`。
- **`databaseName`**（string，可空）：前端默认 `""`。

##### 5.1.12 Drop Index：`DropIndexParams`

对应组件：`custom.components.DropIndexComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`fieldName`**（string，前端必填）：要 drop 的索引字段名。前端默认 `""`。

##### 5.1.13 Compact：`CompactParams`

对应组件：`custom.components.CompactComp`

字段：

- **`compactAll`**（boolean，前端必填）：前端默认 `false`。
- **`collectionName`**（string，可空）：前端默认 `""`。
- **`clustering`**（boolean，前端必填）：注意：Java 字段名是 `isClustering`，JSON key 建议用 `clustering` 或 `isClustering`。前端默认 `false`。

---

#### 5.2 进阶：SearchIterator / Recall / SegmentInfo

##### 5.2.1 SearchIterator：`SearchIteratorParams`

对应组件：`custom.components.SearchIteratorComp`

字段：

- **`collectionName`**（string，可空）
- **`annsFields`**（string，建议必填）：用于从库里 query 出向量样本
- **`vectorFieldName`**（string，建议必填）：传给 SearchIterator 的向量字段名（通常与 `annsFields` 相同）
- **`nq`**（int）
- **`topK`**（int）
- **`batchSize`**（int）：iterator 每次拉取的 batch
- **`outputs`**（list，建议必填）
- **`filter`**（string，可空；作为 `expr`）
- **`metricType`**（string）：只识别 `IP` / `COSINE` / 其它默认 `L2`
- **`params`**（string）：例如 `"{\"level\": 1}"`
- **`numConcurrency`**（int）
- **`runningMinutes`**（long，建议必填且 >0）
- **`randomVector`**（boolean）
- **`useV1`**（boolean）：当前实现未使用该字段（保留）

##### 5.2.2 Recall：`RecallParams`

对应组件：`custom.components.RecallComp`

字段：

- **`collectionName`**（string，可空）
- **`annsField`**（string，建议必填）
- **`searchLevel`**（int）

说明：Recall 使用 `CommonFunction.providerSearchVectorDataset` 采样向量并记录 base id，再以 `topK=1` 搜索并比较命中率。

##### 5.2.3 Segment Info（V1）：`QuerySegmentInfoParams` / `PersistentSegmentInfoParams`

对应组件：

- `custom.components.QuerySegmentInfoComp`（`getQuerySegmentInfo`）
- `custom.components.PersistentSegmentInfoComp`（`getPersistentSegmentInfo`）

字段都只有：

- **`collectionName`**（string，可空）

---

#### 5.3 Collection 结构变更：AddField / Rename / Describe

##### 5.3.1 AddCollectionField：`AddCollectionFieldParams`

对应组件：`custom.components.AddCollectionFieldComp`

字段较多，常用字段如下：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`databaseName`**（string，可空）：前端默认 `""`。
- **`fieldName`**（string，可空）：前端默认 `""`。
- **`dataType`**（DataType 枚举字符串）：前端默认 `""`（占位；建议用枚举名或 `null`/不传，不要传空串）。
- **`defaultValue`**（string，可空）：注意组件内部会按 `dataType` 解析成对应类型。前端默认 `""`。
- **`enableDefaultValue`**（boolean，前端必填）：前端默认 `false`。
- **`isNullable`**（Boolean，前端必填）：前端默认 `true`。
- **`isPrimaryKey/isPartitionKey/isClusteringKey`**（Boolean，可空）
- **`autoID`**（Boolean，可空）
- **`dimension/maxLength/maxCapacity/elementType`**（按类型填写）
- **`enableAnalyzer/enableMatch`**（Boolean，前端必填）：前端默认 `false`。
- **`analyzerParamsList`**（list，可空）：前端默认 `[{paramsKey:"", paramsValue:""}]`（占位；不使用建议传 `[]`）。

##### 5.3.2 RenameCollection：`RenameCollectionParams`

- **`collectionName`**（string，可空）
- **`newCollectionName`**（string）
- **`databaseName`**（string，可空）

##### 5.3.3 DescribeCollection：`DescribeCollectionParams`

- **`collectionName`**（string，可空）
- **`databaseName`**（string，可空）

---

#### 5.4 Database / Alias

##### 5.4.1 CreateDatabase：`CreateDatabaseParams`

- **`databaseName`**（string）
  - 前端必填：是
  - 前端默认值：""（空字符串）

##### 5.4.2 UseDatabase：`UseDatabaseParams`

- **`dataBaseName`**（string）
  - 前端默认值（`useDatabaseEdit.vue` 的 key 是 `databaseName`）：""（空字符串）

##### 5.4.3 CreateAlias / AlterAlias

`CreateAliasParams` / `AlterAliasParams`：

- **`databaseName`**（string，可空）
- **`collectionName`**（string，可空）
- **`alias`**（string）
  - 前端必填：是
  - 前端默认值：""（空字符串）

---

#### 5.5 Workflow 组件：Wait / Loop / Concurrent

##### 5.5.1 Wait：`WaitParams`

- **`waitMinutes`**（long）
  - 前端默认值：1

##### 5.5.2 Loop：`LoopParams`

对应组件：`custom.components.LoopComp`

- **`paramComb`**（string 或 object）：一个“内嵌的 customize_params JSON”（会再次走 `ComponentSchedule.runningSchedule`）。前端默认 `{}`（空对象）。
- **`runningMinutes`**（int）：循环总时长上限（0 表示无限/很大）。前端默认 `0`。
- **`cycleNum`**（int）：循环次数上限（0 表示无限/很大）。前端默认 `1`。

##### 5.5.3 Concurrent：`ConcurrentParams`

对应组件：`custom.components.ConcurrentComp`

- **`paramComb`**（string 或 object）：一个“内嵌的 customize_params JSON”，内部步骤会并行执行。前端默认 `{}`（空对象）。

> 注意：`ConcurrentParams` 的 `paramComb` 内部 key 也必须是 `ClassName_index`（因为内部也会按 `_数字` 排序）。

---

#### 5.6 Cloud/运维相关（如果你的客户只关心 Milvus 功能测试，可忽略）

这些组件主要通过 `custom.utils.*ServiceUtils` 调用内部服务（cloud-service / rm-service / ops-service）。字段含义以对应 `*Params` 类为准：

- `CreateInstanceParams`（`CreateInstanceComp`）
- `DeleteInstanceParams`（`DeleteInstanceComp`）
- `StopInstanceParams`（`StopInstanceComp`）
- `ResumeInstanceParams`（`ResumeInstanceComp`）
- `RestartInstanceParams`（`RestartInstanceComp`）
- `RollingUpgradeParams`（`RollingUpgradeComp`）
- `ModifyParams`（`ModifyParamsComp`）
- `UpdateIndexPoolParams`（`UpdateIndexPoolComp`）
- `AlterInstanceIndexClusterParams`（`AlterInstanceIndexClusterComp`）
- `RestoreBackupParams`（`RestoreBackupComp`）

前端默认值/必填速查（来自 `*Edit.vue`，详细字段见 `custom.entity/*Params` 的 Javadoc）：

- **`CreateInstanceParams`**（`createInstanceEdit.vue`）
  - 必填：`instanceName`/`dbVersion`/`replica`/`rootPassword`/`roleUse`/`useHours`
  - 默认：`cuType=class-1-enterprise`，`architecture=2`，`replica=1`，`rootPassword=Milvus123`，`roleUse=root`，`useHours=10`，`instanceType=1`，`bizCritical=false`，`monopolized=false`，`dbVersion=latest-release`
  - `streamingNodeParams` 默认：`{replicaNum:"", cpu:"", memory:"", disk:""}`（占位；建议按后端类型传值）
- **`DeleteInstanceParams`**（`deleteInstanceEdit.vue`）
  - 必填：`useCloudTestApi`（注意后端字段名是 `useOPSTestApi`，需要映射/改 key）
  - 默认：`useCloudTestApi=false`，`instanceId/accountEmail/accountPassword=""`
- **`RollingUpgradeParams`**（`rollingUpdateEdit.vue`）
  - 必填：`targetDbVersion`
  - 默认：`targetDbVersion=""`，`forceRestart=true`
- **`ModifyParams`**（`modifyParamsEdit.vue`）
  - 必填：`paramsList`
  - 默认：`needRestart=true`，`paramsList=[{paramName:"", paramValue:""}]`
- **`UpdateIndexPoolParams`**（`updateIndexPoolEdit.vue`）
  - 默认：`managerImageTag=""`，`workerImageTag=""`，`indexClusterId=""`（占位；后端是 int）
- **`AlterInstanceIndexClusterParams`**（`alterInstanceIndexClusterEdit.vue`）
  - 默认：`instanceId=""`，`indexClusterId=""`（占位；后端是 int）
- **`RestoreBackupParams`**（`restoreBackupEdit.vue`）
  - 必填：`backupId`、`restorePolicy`
  - 默认：`notChangeStatus=false`，`restorePolicy=1`，`skipCreateCollection=false`，`toInstanceId=""`，`truncateBinlogByTs=false`，`withRBAC=false`

---

### 6. “枚举/可选值”字典（LLM 生成 JSON 必须知道）

#### 6.1 `env`（System Property）

来自 `custom.config.EnvEnum` 的 `region` 字段，可选值（大小写不敏感）：

- `devops`
- `fouram`
- `awswest`
- `gcpwest`
- `azurewest`
- `alihz`
- `tcnj`
- `hwc`

#### 6.2 `dataset`（Insert/Upsert）

代码中按 `toLowerCase()` switch，支持：

- `random`：随机生成向量（最通用，推荐默认）
- `gist` / `deep` / `sift` / `laion`：从固定路径读取 `.npy` 数据集（依赖运行机器上存在 `DatasetEnum.path`）

#### 6.3 `collectionRule`

代码中支持：

- `random`：从 `globalCollectionNames` 随机选
- `sequence`：按 `*_CollectionIndex` 轮转
- 空/不传：默认使用最后一个 collection（`globalCollectionNames.get(last)`）

#### 6.4 Milvus SDK 枚举（必须用“枚举常量名”）

很多字段是 Milvus SDK 的 enum（fastjson 反序列化时通常要求 **常量名严格匹配**）。

##### 6.4.1 `DataType`（用于 FieldParams / AddCollectionFieldParams）

类型来自 `io.milvus.v2.common.DataType`。代码中实际用到/判断的常见值：

- 标量：`Int64` / `Int32` / `Int16` / `Int8` / `Bool` / `Float` / `Double`
- 字符串：`VarChar` / `String`
- 复杂：`Array` / `JSON` / `Geometry`
- 向量：`FloatVector` / `BinaryVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector` / `SparseFloatVector`

> 注意：是 **`VarChar`** 不是 `Varchar`。

##### 6.4.2 `IndexType` / `MetricType`（用于 IndexParams）

类型来自：

- `io.milvus.v2.common.IndexParam.IndexType`
- `io.milvus.v2.common.IndexParam.MetricType`

推荐默认（最省配置）：

- `indextype`: `AUTOINDEX`
- `metricType`: `L2`（或 `COSINE`/`IP`）

##### 6.4.3 `FunctionType`（用于 FunctionParams）

类型来自 `io.milvus.common.clientenum.FunctionType`，常见：`BM25`（用于稀疏向量/文本检索）。

---

### 7. 数据/过滤生成规则：`GeneralDataRole`（高级但很有用）

`GeneralDataRole`（`custom.pojo.GeneralDataRole`）可用于两类场景：

- **Insert/Upsert 的数据生成**：通过 `generalDataRoleList` 控制某些字段的取值分布/序列。
- **Search/Query 的 filter 参数替换**：通过 `generalFilterRoleList` 动态生成 filter 中的 `$占位符`。

结构：

- **`fieldName`**：字段名
- **`prefix`**：字符串前缀（用于 VarChar/String）
- **`sequenceOrRandom`**：`sequence` 或其它（其它视为 random）
- **`randomRangeParamsList`**：范围与占比（见下）

`RandomRangeParams`（`custom.pojo.RandomRangeParams`）：

- **`start`** / **`end`**：范围
- **`rate`**：概率（0~1）。`advanceRandom()` 会按概率桶选择范围再取随机值。

filter 占位符规则（Search/Query）：

- 在 `filter` 字符串里写 `$<fieldName>`，例如：`"id_pk > $id_pk"`
- 执行时会把 `$id_pk` 替换成 `prefix + 数值`（数值按 random/sequence 规则生成）

> 说明：`sequence` 的 filter 生成目前只支持 `randomRangeParamsList` 的第一条规则（见 `advanceSequenceForSearch`）。

---

### 8. 推荐的 LLM 输出格式（给 LLM 的硬约束）

如果你希望 LLM 根据自然语言生成 `customize_params`，建议把下面规则直接作为 Prompt 的“输出规范”：

- **只输出一个 JSON object**（不要输出解释文字）。
- 顶层 key 必须为：`<ParamsClassName>_<序号>`，序号从 0 递增即可。
- **所有 List 字段必须显式给出**（哪怕是 `[]`），避免组件里 NPE。
- 所有 Milvus enum 字段必须输出 **正确的枚举常量名**（例如 `VarChar`、`FloatVector`、`AUTOINDEX`、`L2`）。
- 未指定 collection 时，`collectionName` 设为 `""`（空字符串），并尽量不要同时填 `collectionRule`。

你也可以让 LLM 一并产出：

- `initial_params`（建议至少 `{ "cleanCollection": false }`）
- `customize_params`（核心）

---

### 9. 一个“最小可用”示例（建议让 LLM 从它改）

> 注意：仓库内 `src/main/resources/example/base.json` 等示例文件会随代码演进调整；如果你要喂给 LLM，请优先以本文给出的字段名/必填字段/枚举取值为准。

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {"dataType": "Int64", "fieldName": "id_pk", "primaryKey": true, "autoId": false},
      {"dataType": "VarChar", "fieldName": "varchar_1", "maxLength": 256},
      {"dataType": "FloatVector", "fieldName": "vec", "dim": 128}
    ]
  },
  "CreateIndexParams_1": {
    "collectionName": "",
    "databaseName": "",
    "indexParams": [
      {"fieldName": "vec", "indextype": "AUTOINDEX", "metricType": "L2"}
    ]
  },
  "LoadParams_2": {
    "loadAll": false,
    "collectionName": "",
    "loadFields": [],
    "skipLoadDynamicField": false
  },
  "InsertParams_3": {
    "collectionName": "",
    "collectionRule": "",
    "partitionName": "",
    "startId": 0,
    "numEntries": 10000,
    "batchSize": 1000,
    "numConcurrency": 5,
    "dataset": "random",
    "runningMinutes": 0,
    "retryAfterDeny": false,
    "ignoreError": false,
    "generalDataRoleList": []
  },
  "FlushParams_4": {
    "flushAll": false,
    "collectionName": ""
  },
  "SearchParams_5": {
    "collectionName": "",
    "collectionRule": "",
    "annsField": "vec",
    "nq": 1,
    "topK": 5,
    "outputs": ["*"],
    "filter": "",
    "numConcurrency": 10,
    "runningMinutes": 1,
    "randomVector": true,
    "searchLevel": 1,
    "indexAlgo": "",
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  },
  "QueryParams_6": {
    "collectionName": "",
    "collectionRule": "",
    "outputs": ["*"],
    "filter": "id_pk > 10",
    "ids": [],
    "partitionNames": [],
    "limit": 5,
    "offset": 0,
    "numConcurrency": 5,
    "runningMinutes": 1,
    "targetQps": 0,
    "generalFilterRoleList": []
  },
  "ReleaseParams_7": {
    "releaseAll": false,
    "collectionName": ""
  }
}
```


