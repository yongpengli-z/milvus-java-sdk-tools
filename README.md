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

### 5. 支持的组件（= 可用的 `*Params` 列表）

最终能跑的 step 以 `ComponentSchedule.callComponentSchedule()` 的 `instanceof` 分发为准。当前支持 **39 种**：

#### 5.1 Milvus 核心链路（最常用）

##### 5.1.1 创建 Collection：`CreateCollectionParams`

对应组件：`custom.components.CreateCollectionComp`

字段（`custom.entity.CreateCollectionParams`）：

- **`collectionName`**（string，可空）：不传/空则自动生成随机名。前端默认：`""`。
- **`shardNum`**（int，前端必填）：前端默认：`1`。
- **`numPartitions`**（int，前端必填）：前端默认：`0`。
  - **重要约束**：当 `numPartitions > 0` 时，`fieldParamsList` 中**必须至少有一个字段的 `partitionKey` 为 `true`**，否则 Milvus 会报错：`num_partitions should only be specified with partition key field enabled`。
  - 如果不需要分区，请设置 `numPartitions: 0`，且所有字段的 `partitionKey: false`。
- **`enableDynamic`**（boolean，前端必填）：是否开启动态列。前端默认：`false`。
- **`fieldParamsList`**（list，前端必填）：字段定义（见 `FieldParams`）。前端默认：2 个字段（`Int64_0` 作为 PK + `FloatVector_1` 向量字段）。
  - **向量字段约束**：一个 collection **必须至少包含一个向量字段**。向量字段可以是：
    - 正常的顶层向量字段（如 `FloatVector`、`BinaryVector` 等）
    - 或者 Array of Struct 中的向量子字段
  - **示例建议**：在生成示例/demo 时，建议提供一个正常的顶层向量字段（如 `FloatVector`），这样更直观易懂
- **`functionParams`**（object，可空）：function（例如 BM25）配置（见 `FunctionParams`）。前端默认：`{functionType:"", name:"", inputFieldNames:[], outputFieldNames:[]}`（注意 `functionType=""` 是占位，建议用 `null`/不传）。
- **`properties`**（list，前端必填）：collection properties（key/value）。前端默认：`[{propertyKey:"", propertyValue:""}]`（占位；不需要可传 `[]`）。
- **`databaseName`**（string，可空）：前端默认：`""`。

`fieldParamsList` 的元素类型：`custom.entity.FieldParams`

- **`fieldName`**（string）：前端默认模板里是 `Int64_0` / `FloatVector_1`；新增行默认 `""`。
- **`dataType`**（enum 字符串，见下文“DataType 枚举”）：前端默认模板里是 `Int64` / `FloatVector`；新增行默认 `""`（占位，建议不要传空串）。
- **`primaryKey`**（boolean）：注意：Java 字段名是 `isPrimaryKey`，但示例/fastjson 常用 key 为 `primaryKey`。前端默认模板里首字段为 `true`。**建议显式给值**（非主键字段给 `false`）。
- **`autoId`**（boolean）：仅主键字段可用。前端默认：`false`。**建议显式给 `false`**（即使不启用 AutoID）。
- **`dim`**（int）：向量维度（vector 类型必填）。前端默认模板里向量字段为 `768`。
- **`maxLength`**（int）：VarChar 必填。前端新增行默认 `null`（占位）；生成 JSON 时请传数字或不传（不要传 `""`）。
- **`maxCapacity`**（int）：Array 必填。前端新增行默认 `null`（占位）；生成 JSON 时请传数字或不传（不要传 `""`）。
- **`elementType`**（enum）：Array 元素类型。前端新增行默认 `null`（占位）；生成 JSON 时请传枚举名或 `null`/不传（不要传 `""`/`0` 占位）。
  - **重要**：当 `dataType=Array` 且 `elementType=Struct` 时，需要同时设置 `structSchema` 字段。
- **`structSchema`**（list，可空）：Struct Schema（仅当 `dataType=Array` 且 `elementType=Struct` 时生效）。用于定义 Array of Struct 中 Struct 的子字段列表。前端默认：`null` 或 `[]`。
  - **Struct 子字段类型**：`custom.entity.StructFieldParams`
    - **`fieldName`**（string）：Struct 子字段名
    - **`dataType`**（enum）：Struct 子字段类型（**不能是 Struct、Array、Json**）
    - **`dim`**（int）：向量维度（仅 Vector 类型生效）
    - **`maxLength`**（int）：VarChar/String 最大长度（仅 VarChar/String 生效）
    - **`isNullable`**（boolean）：是否允许为 NULL（前端默认：`false`）
  - **限制**：
    - Struct 只能作为 Array 的元素类型使用（`dataType=Array`，`elementType=Struct`）
    - Struct 子字段不能是 Struct、Array、Json
    - Struct 可以包含向量字段，从而实现 Array of Vector
    - Struct 暂不支持 nullable 字段（但字段中保留该属性以备将来使用）
- **`partitionKey`**（boolean）：前端默认：`false`。**建议显式给 `false`**（即使不是分区键）。
  - **重要约束**：如果 `CreateCollectionParams.numPartitions > 0`，则**必须至少有一个字段的 `partitionKey` 为 `true`**，否则创建 collection 会失败。
  - 如果 `numPartitions = 0`，则所有字段的 `partitionKey` 都应为 `false`。
- **`nullable`**（boolean）：前端默认：`false`（且主键/向量字段会禁用 nullable=true）。**建议显式给 `false`**（除非确实需要可空）。
- **`enableMatch`**（boolean）：前端默认：`false`。**建议显式给 `false`**（即使不启用匹配功能）。
- **`enableAnalyzer`**（boolean）：前端默认：`false`。**建议显式给 `false`**（即使不启用分析器）。
- **`analyzerParamsList`**（list，可空）：前端新增行默认 `[{paramsKey:"", paramsValue:""}]`（占位；不需要可传 `[]`）。

> **重要提示**：虽然这些 boolean 字段（`autoId`、`partitionKey`、`nullable`、`enableMatch`、`enableAnalyzer`）的前端默认值都是 `false`，但**建议在生成 JSON 时显式给出 `false`**，以避免后端反序列化时因字段缺失导致的不确定性。特别是当 LLM 生成 JSON 时，明确写出这些字段有助于提高可读性和避免歧义。

**Array of Struct 使用示例**：

Array of Struct 允许在一个字段中存储多个结构体元素，每个结构体可以包含多个子字段（包括向量字段），从而实现 Array of Vector 的功能。

**注意**：虽然一个 collection 必须至少包含一个向量字段，且这个向量字段可以是 Array of Struct 中的向量子字段，但在示例中建议同时提供一个正常的顶层向量字段（如下面的 `embedding` 字段），这样更直观易懂。

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id_pk",
        "primaryKey": true,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "Array",
        "fieldName": "clips",
        "elementType": "Struct",
        "maxCapacity": 100,
        "structSchema": [
          {
            "fieldName": "frame_number",
            "dataType": "Int32",
            "isNullable": false
          },
          {
            "fieldName": "clip_embedding",
            "dataType": "FloatVector",
            "dim": 128,
            "isNullable": false
          },
          {
            "fieldName": "clip_desc",
            "dataType": "VarChar",
            "maxLength": 1024,
            "isNullable": false
          },
          {
            "fieldName": "description_embedding",
            "dataType": "FloatVector",
            "dim": 128,
            "isNullable": false
          }
        ],
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      }
    ],
    "functionParams": null,
    "properties": [],
    "databaseName": ""
  }
}
```

**关键点**：
- `dataType` 必须为 `Array`
- `elementType` 必须为 `Struct`
- `structSchema` 必须提供，包含 Struct 的子字段定义
- Struct 子字段可以包含向量类型（如 `FloatVector`），从而实现 Array of Vector
- 数据生成时，`genCommonData` 方法会自动从 `collectionSchema.getStructFields()` 获取 Struct 字段信息并生成对应的数据

**Array of Struct 索引创建**：

**重要**：Array of Struct 中的向量字段**必须建索引**，否则无法进行搜索操作。

为 Struct 中的向量字段创建索引时，需要使用 `fieldName[subFieldName]` 格式（按照 Array 的规则）：

```json
{
  "CreateIndexParams_1": {
    "collectionName": "",
    "databaseName": "",
    "indexParams": [
      {
        "fieldName": "clips[clip_embedding]",
        "indextype": "HNSW",
        "metricType": "MAX_SIM_L2"
      },
      {
        "fieldName": "clips[description_embedding]",
        "indextype": "HNSW",
        "metricType": "MAX_SIM_IP"
      }
    ]
  }
}
```

**注意**：
- **Struct 中的向量字段必须建索引**：如果 Array of Struct 中包含向量字段，必须为这些向量字段创建索引，否则无法进行搜索
- Struct 中的向量字段使用特殊的索引类型和 MetricType（如 `MAX_SIM_L2`、`MAX_SIM_IP`、`MAX_SIM_COSINE`）
- **字段名格式**：`<structFieldName>[<subFieldName>]`（按照 Array 的规则，使用方括号 `[]` 而不是点号 `.`）
  - ✅ 正确：`clips[clip_embedding]`
  - ❌ 错误：`clips.clip_embedding` 或 `clips_clip_embedding`

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
  - **重要约束**：索引类型与向量类型有强约束关系（见下文"向量类型与索引类型/MetricType 约束"）。
- **`metricType`**（enum 字符串，可空）：`io.milvus.v2.common.IndexParam.MetricType`
  - **重要约束**：MetricType 与向量类型有强约束关系（见下文"向量类型与索引类型/MetricType 约束"）。
- **`jsonPath/jsonCastType`**（string，可空）：用于 JSON 字段或 dynamic field 的标量字段索引
  - `jsonPath`：JSON 路径，例如 `field["key1"]["key2"]`、`field["key1"][0]["key2"]`
  - `jsonCastType`：目标类型，例如 `varchar`、`int64`、`double`、`bool`
  - **使用场景**：对 JSON 字段或 dynamic field 中的标量字段建索引时，必须同时设置这两个字段
  - **索引类型**：通常使用 `STL_SORT` 或 `AUTOINDEX`（不需要 MetricType）
- **`buildLevel`**（string，可空）：例如 HNSW build level（仅部分向量类型和环境使用）
  - **支持范围**：仅 `FloatVector`、`Float16Vector`、`BFloat16Vector` 支持 `buildLevel` 参数
  - **环境约束**：**仅在云上环境**（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`）支持 `buildLevel` 参数；本地环境（`devops`、`fouram`）不支持
  - 常用值：`"1"`（Balanced，默认）、`"2"`（Performance）、`"0"`（Memory）

> 重要：如果你想"让系统自动给所有向量字段建索引"，请传 `indexParams: []`（不要省略该字段）。系统会根据向量类型自动选择对应的索引类型和 MetricType。

**向量类型与索引类型/MetricType 约束**（LLM 生成 JSON 时必须遵循）：

- **`FloatVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector`**：
  - 推荐索引类型：`AUTOINDEX` 或 `HNSW`
  - 支持的 MetricType：`L2`（默认）、`COSINE`、`IP`
  - 支持 `buildLevel`：仅 `FloatVector` / `Float16Vector` / `BFloat16Vector`，且**仅在云上环境**（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`）支持；本地环境不支持

- **`BinaryVector`**：
  - 推荐索引类型：`AUTOINDEX` 或 `BIN_IVF_FLAT`
  - **必须使用的 MetricType**：`HAMMING` 或 `JACCARD`（**不能使用 L2/IP/COSINE**）
  - 不支持 `buildLevel`

- **`SparseFloatVector`**：
  - 推荐索引类型：`AUTOINDEX` 或 `SPARSE_WAND`
  - **MetricType 选择规则**（**特殊情况**）：
    - 如果 `SparseFloatVector` 字段是由 **BM25 function** 生成的（即该字段在 `CreateCollectionParams.functionParams.outputFieldNames` 中），**必须使用 `BM25`**
    - 否则，使用 `IP`
    - **不能使用**：`L2`、`COSINE`、`HAMMING`
  - 不支持 `buildLevel`
  - **示例**：
    - BM25 function 生成的稀疏向量：`{"fieldName": "sparse_embedding", "indextype": "AUTOINDEX", "metricType": "BM25"}`
    - 普通稀疏向量：`{"fieldName": "sparse_vec", "indextype": "AUTOINDEX", "metricType": "IP"}`

- **Array of Struct 中的向量字段**（**特殊索引类型**）：
  - **必须建索引**：Array of Struct 中的向量字段**必须建索引**，否则无法进行搜索操作
  - **字段名格式**：`<structFieldName>[<subFieldName>]`（按照 Array 规则使用方括号 `[]`），例如 `clips[clip_embedding]`
    - ✅ 正确：`clips[clip_embedding]`
    - ❌ 错误：`clips.clip_embedding` 或 `clips_clip_embedding`
  - **索引类型**：`HNSW`（当前支持）
  - **MetricType**：`MAX_SIM_L2`、`MAX_SIM_IP`、`MAX_SIM_COSINE`（**必须使用 MAX_SIM 系列**，不能使用普通的 L2/IP/COSINE）
  - **说明**：Array of Struct 中的向量字段使用 Embedding List Index（嵌入列表索引），用于搜索包含多个向量的记录
  - **示例**：
    ```json
    {
      "fieldName": "clips[clip_embedding]",
      "indextype": "HNSW",
      "metricType": "MAX_SIM_L2"
    }
    ```

**标量字段索引类型**（LLM 生成 JSON 时必须遵循）：

- **`VarChar` / `String` / `Int64` / `Int32` / `Int16` / `Int8` / `Float` / `Double` / `Bool`**：
  - 推荐索引类型：`STL_SORT` 或 `AUTOINDEX`
  - **不需要 MetricType**（MetricType 仅用于向量字段）
  - 用途：加速标量字段的查询、排序和过滤

- **`JSON` / Dynamic Field**：
  - 推荐索引类型：`STL_SORT` 或 `AUTOINDEX`
  - **必须设置**：`jsonPath`（JSON 路径）和 `jsonCastType`（目标类型）
  - **不需要 MetricType**
  - 示例：`{"fieldName": "json_field", "indextype": "STL_SORT", "jsonPath": "field[\"key1\"]", "jsonCastType": "varchar"}`

详细约束表见下文"6.4.2 IndexType / MetricType"章节。

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
  - **性能测试建议**：当需要测试 Insert 性能时，推荐添加多个 `InsertParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20），来递增压力，观察不同并发级别下的性能表现。
- **`dataset`**（string，前端必填）：`random/gist/deep/sift/laion`。前端默认 `random`。
- **`runningMinutes`**（long，前端必填）：Insert 中该字段>0 时会成为“时间上限”，否则以数据量批次数为准。前端默认 `0`。
- **`retryAfterDeny`**（boolean，可空）：禁写后是否等待重试。前端默认 `false`。
- **`ignoreError`**（boolean，可空）：出错是否忽略继续。前端默认 `false`。
- **`generalDataRoleList`**（list，可空）：数据生成规则（见 `GeneralDataRole`）。前端默认是“带 1 条空规则”的占位数组；**如果你不使用该能力，建议直接传 `[]`**。

**Array of Struct 数据生成说明**：
- Insert/Upsert 组件会自动识别 collection 中的 Array of Struct 字段
- 数据生成时，`genCommonData` 方法会从 `collectionSchema.getStructFields()` 获取 Struct 字段信息
- 每个 Array of Struct 字段会生成随机数量的结构体元素（数量在 1 到 `maxCapacity` 之间）
- Struct 子字段的数据会根据其类型自动生成：
  - 向量字段：生成随机向量
  - 字符串字段：生成随机字符串
  - 数值字段：生成递增或随机数值
- **注意**：Array of Struct 字段不会出现在 `fieldSchemaList` 中，只会出现在 `structFieldSchemaList` 中

##### 5.1.5 Search：`SearchParams`

对应组件：`custom.components.SearchComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`annsField`**（string，前端必填 & 强烈建议显式指定）：向量字段名。前端默认 `vectorField_1`（注意：`createCollectionEdit.vue` 默认向量字段名是 `FloatVector_1`，两者模板不强绑定，实际以你的向量字段名为准）。
  - **Array of Struct 中的向量字段**：如果搜索 Struct 中的向量字段，字段名格式为 `<structFieldName>[<subFieldName>]`（按照 Array 规则使用方括号），例如 `clips[clip_embedding]`。**注意**：该向量字段必须已建索引，否则搜索会失败。
- **`nq`**（int，前端必填）：query vectors 数量。前端默认 `1`。
- **`topK`**（int，前端必填）：前端默认 `1`。
- **`outputs`**（list，建议必填）：输出字段。前端默认 `[]`；不需要输出请传 `[]`。
- **`filter`**（string，可空）：Milvus expr（可包含 `$fieldName` 占位符，见下文）。前端默认 `""`。
- **`numConcurrency`**（int，前端必填）：前端默认 `10`。
  - **性能测试建议**：当需要测试 Search 性能时，推荐添加多个 `SearchParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20、50），来递增压力，观察不同并发级别下的性能表现。
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
  - **重要约束**：**`filter` 和 `ids` 必须至少传一个**（不能同时为空）。
  - 如果传了 `ids`（非空数组），可以不传 `filter`（传空字符串 `""`）。
  - 如果传了 `filter`（非空字符串），可以不传 `ids`（传空数组 `[]`）。
  - **推荐写法**：使用主键字段的条件表达式，例如：
    - 主键为 Int64：`"id_pk >= 0"` 或 `"id_pk > 0"`
    - 主键为 VarChar：`"id_pk >= \"0\""` 或 `"id_pk > \"\""`
  - 如果 collection 的主键字段名不是 `id_pk`，请替换为实际的主键字段名。
- **`ids`**（list，建议必填）：前端默认 `[]`（但页面用 `tempIDs` 字符串输入再 split）。
  - **重要约束**：**`filter` 和 `ids` 必须至少传一个**（不能同时为空）。
  - 如果传了 `filter`（非空字符串），可以不传 `ids`（传空数组 `[]`）。
  - 如果传了 `ids`（非空数组），可以不传 `filter`（传空字符串 `""`）。
- **`partitionNames`**（list，可空）：前端初始模板里是 `""`（占位，失焦后会 split 成数组）；**建议生成 JSON 时直接用 `[]` 或 `["p1","p2"]`**。
- **`limit`**（long，可空）：前端初始模板里是 `""`（占位）；**建议生成 JSON 时传数字或不传**。
- **`offset`**（long，前端必填）：前端默认 `0`。
- **`numConcurrency`**（int，前端必填）：前端默认 `10`。
  - **性能测试建议**：当需要测试 Query 性能时，推荐添加多个 `QueryParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20、50），来递增压力，观察不同并发级别下的性能表现。
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
  - **性能测试建议**：当需要测试 Upsert 性能时，推荐添加多个 `UpsertParams` 组件，设置不同的 `numConcurrency`（如 1、5、10、20），来递增压力，观察不同并发级别下的性能表现。
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

**用途**：Flush 操作用于将 **growing segment** 刷成 **sealed segment**，通常在 Insert 完成后执行，确保数据落盘并触发后续的索引构建。

字段：

- **`flushAll`**（boolean，前端必填）：true 则 flush 实例内所有 collection。前端默认 `false`。
- **`collectionName`**（string，可空）：`flushAll=false` 时使用。前端默认 `""`。

**使用场景**：
- Insert 完成后立即 Flush，确保数据落盘
- 在并发读写测试中，Flush 应该在 Insert 之后、Search/Query 之前执行（或与 Search/Query 并行）
- 如果只是测试写入性能，可以在 Insert 后立即 Flush

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

##### 5.2.2 HybridSearch：`HybridSearchParams`

对应组件：`custom.components.HybridSearchComp`

**用途**：HybridSearch（混合搜索）支持在同一个 collection 中对多个向量字段进行搜索，并使用融合策略（RRF 或 WeightedRanker）合并结果。这是 Milvus 2.4+ 版本引入的功能。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`searchRequests`**（list，前端必填）：混合搜索请求列表，每个元素代表一个向量字段的搜索请求。
  - **`annsField`**（string）：向量字段名
  - **`topK`**（int）：该字段的 topK
  - **`searchParams`**（object，可空）：搜索参数 Map，例如 `{"level": 1}`
  - **`filter`**（string，可空）：该字段的 Milvus expr 过滤表达式（可包含 `$fieldName` 占位符）。每个搜索请求可以有自己的 filter。前端默认 `""`（表示不过滤）
  - **注意**：`metricType` 字段已不再使用，`AnnSearchReq` 构建时不会设置 MetricType，Milvus 会根据索引配置自动使用对应的 MetricType。
- **`ranker`**（string，前端必填）：融合策略类型，可选值：
  - `"RRF"`：Reciprocal Rank Fusion（倒数排名融合），默认值
  - `"WeightedRanker"`：加权排序
- **`rankerParams`**（object，可空）：融合策略参数 Map
  - RRF：`{"k": 60}`（k 为常数，默认 60）
  - WeightedRanker：`{"weights": [0.5, 0.5]}`（权重列表，长度需与 searchRequests 数量一致）
- **`topK`**（int，前端必填）：最终返回的候选数量。前端默认 `10`。
- **`nq`**（int，前端必填）：query 向量数量。前端默认 `1`。
- **`randomVector`**（boolean，前端必填）：是否每次请求随机选择 query 向量。前端默认 `true`。
- **`outputs`**（list，建议必填）：输出字段。前端默认 `[]`。
- **`numConcurrency`**（int，前端必填）：并发线程数。前端默认 `10`。
- **`runningMinutes`**（long，前端必填）：运行时长（分钟）。前端默认 `10`。
- **`targetQps`**（double，可空）：目标 QPS。前端默认 `0`（0=不限制）。
- **`generalFilterRoleList`**（list，可空）：filter 占位符替换规则列表。前端默认是“带 1 条空规则”的占位数组；不使用建议传 `[]`。
- **`ignoreError`**（boolean，可空）：是否忽略错误继续搜索。前端默认 `false`。

**使用场景**：
- 多模态搜索：例如使用 ResNet 和 CLIP 分别提取图片和文本特征，存储为不同向量列，然后进行混合搜索
- 多向量列融合：当同一个 collection 中有多个向量字段时，可以使用 HybridSearch 进行融合搜索

**示例 JSON**：
```json
{
  "HybridSearchParams_0": {
    "collectionName": "",
    "collectionRule": "",
    "searchRequests": [
      {
        "annsField": "image_vector",
        "topK": 10,
        "searchParams": {"level": 1},
        "filter": ""
      },
      {
        "annsField": "text_vector",
        "topK": 10,
        "searchParams": {"level": 1},
        "filter": ""
      }
    ],
    "ranker": "RRF",
    "rankerParams": {"k": 60},
    "topK": 10,
    "nq": 1,
    "randomVector": true,
    "outputs": ["*"],
    "filter": "",
    "numConcurrency": 10,
    "runningMinutes": 1,
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  }
}
```

##### 5.2.3 Recall：`RecallParams`

对应组件：`custom.components.RecallComp`

字段：

- **`collectionName`**（string，可空）
- **`annsField`**（string，建议必填）
- **`searchLevel`**（int）

说明：Recall 使用 `CommonFunction.providerSearchVectorDataset` 采样向量并记录 base id，再以 `topK=1` 搜索并比较命中率。

##### 5.2.4 Segment Info（V1）：`QuerySegmentInfoParams` / `PersistentSegmentInfoParams`

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

**重要约束**：
- **一个 collection 只能有一个别名**（Milvus 的限制）
- **`CreateAliasParams`**：用于给 collection 创建第一个别名
- **`AlterAliasParams`**：用于修改 collection 的别名（如果 collection 已有别名，要使用新别名时，必须使用 `AlterAliasParams` 来修改，而不能使用 `CreateAliasParams` 创建新别名）
- 如果尝试给已有别名的 collection 创建新别名，会导致错误

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

**使用场景**：
- **批量创建多个 collection 并插入数据**：当需要创建多个 collection（如 50 个），每个 collection 插入一定数量的数据时，**应该使用 `LoopParams` 组件来循环执行**，而不是生成多个重复的 `CreateCollectionParams` 和 `InsertParams` 组件。
- **重复执行某个操作序列**：当需要重复执行一系列操作（如创建 collection → 建索引 → 加载 → 插入 → 搜索）多次时，使用 `LoopParams` 可以简化配置并提高可维护性。

> **重要提示**：`LoopParams` 的 `paramComb` 内部 key 也必须是 `ClassName_index`（因为内部也会按 `_数字` 排序）。

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
  - **`dbVersion` 特殊值说明**：
    - `latest-release`：后端会获取最新的 release 版本镜像
    - `nightly`：后端会自动查找最新的 nightly 版本镜像（通过关键字查询匹配的镜像，返回最新的一个）
    - 其他值：后端会通过关键字查询匹配的镜像，并返回最新的一个
  - **`streamingNodeParams`**（已废弃，不再需要）：创建实例时不再需要配置 streaming node 参数，可以省略该字段或传 `null`
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

**本地环境**（支持所有索引类型）：
- `devops`
- `fouram`

**云上环境**（**必须使用 `AUTOINDEX`**）：
- `awswest`（AWS 云）
- `gcpwest`（GCP 云）
- `azurewest`（Azure 云）
- `alihz`（阿里云）
- `tcnj`（腾讯云）
- `hwc`（华为云）

> **重要约束**：云上环境（除 `devops` 和 `fouram` 外的所有环境）**必须使用 `AUTOINDEX` 作为索引类型**，不能使用 `HNSW`、`BIN_IVF_FLAT`、`SPARSE_WAND` 等显式索引类型。如果 LLM 生成 JSON 时指定了云上环境，应自动将索引类型设置为 `AUTOINDEX`。

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
- 复杂：`Array` / `JSON` / `Geometry` / `Struct`
- 向量：`FloatVector` / `BinaryVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector` / `SparseFloatVector`

> 注意：
> - 是 **`VarChar`** 不是 `Varchar`。
> - **`Struct`** 只能作为 `Array` 的 `elementType` 使用（即 `dataType=Array`，`elementType=Struct`），不能单独作为字段类型。

##### 6.4.2 `IndexType` / `MetricType`（用于 IndexParams）

类型来自：

- `io.milvus.v2.common.IndexParam.IndexType`
- `io.milvus.v2.common.IndexParam.MetricType`

**重要：索引类型和 MetricType 与字段类型有强约束关系**，LLM 生成 JSON 时必须遵循：

#### 向量字段索引类型

| 向量类型（DataType） | 推荐索引类型（IndexType） | 支持的 MetricType | 说明 |
|---------------------|------------------------|------------------|------|
| `FloatVector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | 最常用的浮点向量，支持所有 MetricType；**仅在云上环境支持 `buildLevel`** |
| `Float16Vector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | 16位浮点向量，**仅在云上环境支持 `buildLevel`** |
| `BFloat16Vector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | BFloat16 向量，**仅在云上环境支持 `buildLevel`** |
| `Int8Vector` | `HNSW` / `AUTOINDEX` | `L2` / `COSINE` / `IP` | 8位整数向量 |
| `BinaryVector` | `BIN_IVF_FLAT` / `AUTOINDEX` | `HAMMING` / `JACCARD` | 二进制向量，**必须使用 HAMMING 或 JACCARD**，不能使用 L2/IP/COSINE |
| `SparseFloatVector` | `SPARSE_WAND` / `AUTOINDEX` | `IP` / `BM25` | 稀疏向量，**特殊情况**：如果是由 BM25 function 生成的，必须使用 `BM25`；否则使用 `IP`。不能使用 L2/COSINE/HAMMING |
| Array of Struct 中的向量字段 | `HNSW` | `MAX_SIM_L2` / `MAX_SIM_IP` / `MAX_SIM_COSINE` | **必须建索引**。Struct 中的向量字段，字段名格式：`<structFieldName>[<subFieldName]>`（按照 Array 规则使用方括号），例如 `clips[clip_embedding]`。**必须使用 MAX_SIM 系列 MetricType**，不能使用普通的 L2/IP/COSINE |

#### 标量字段索引类型

| 标量类型（DataType） | 推荐索引类型（IndexType） | MetricType | 说明 |
|---------------------|------------------------|-----------|------|
| `VarChar` / `String` | `STL_SORT` / `AUTOINDEX` | 不需要（标量字段不使用 MetricType） | 字符串字段索引，用于加速字符串查询和排序 |
| `Int64` / `Int32` / `Int16` / `Int8` | `STL_SORT` / `AUTOINDEX` | 不需要 | 整数字段索引，用于加速数值查询和排序 |
| `Float` / `Double` | `STL_SORT` / `AUTOINDEX` | 不需要 | 浮点数字段索引，用于加速数值查询和排序 |
| `Bool` | `STL_SORT` / `AUTOINDEX` | 不需要 | 布尔字段索引 |
| `JSON` / Dynamic Field | `STL_SORT` / `AUTOINDEX` | 不需要 | JSON 字段索引，需要配合 `jsonPath` 和 `jsonCastType` 使用 |

**标量字段索引说明**：

- **`STL_SORT`**：标量字段的标准索引类型，用于加速标量字段的查询、排序和过滤
- **`AUTOINDEX`**：系统自动选择索引类型（对于标量字段，通常也会选择 `STL_SORT`）
- **`MetricType`**：标量字段**不需要** MetricType（MetricType 仅用于向量字段的距离计算）
- **JSON 字段索引**：如果对 JSON 字段或 dynamic field 中的标量字段建索引，需要：
  - 设置 `jsonPath`：指定 JSON 路径，例如 `field["key1"]["key2"]`
  - 设置 `jsonCastType`：指定目标类型，例如 `varchar`、`int64`、`double`、`bool`
  - 索引类型：`STL_SORT` 或 `AUTOINDEX`

**代码中的默认映射**（当使用 `AUTOINDEX` 时，系统会根据字段类型自动选择）：

**向量字段**：
- `FloatVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector` → `HNSW` + `L2`
- `BinaryVector` → `BIN_IVF_FLAT` + `HAMMING`
- `SparseFloatVector` → `SPARSE_WAND` + `IP`（如果是由 BM25 function 生成的，则使用 `BM25`）
- Array of Struct 中的向量字段 → `HNSW` + `MAX_SIM_L2`（**必须建索引**，字段名格式：`<structFieldName>[<subFieldName>]`，按照 Array 规则使用方括号）

**标量字段**：
- `VarChar` / `String` / `Int64` / `Int32` / `Int16` / `Int8` / `Float` / `Double` / `Bool` → `STL_SORT`（不需要 MetricType）
- `JSON` / Dynamic Field → `STL_SORT`（需要配合 `jsonPath` 和 `jsonCastType`）

**`buildLevel` 参数**（仅部分向量类型和环境支持）：

- **支持的向量类型**：`FloatVector`、`Float16Vector`、`BFloat16Vector`（仅 float 类型向量）
- **环境约束**：**仅在云上环境**（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`）支持；本地环境（`devops`、`fouram`）不支持
- **不支持的向量类型**：`BinaryVector`、`Int8Vector`、`SparseFloatVector`
- 常用值：`"1"`（Balanced，默认）、`"2"`（Performance）、`"0"`（Memory）

**环境与索引类型的约束**：

- **本地环境**（`devops`、`fouram`）：可以使用所有索引类型（`HNSW`、`BIN_IVF_FLAT`、`SPARSE_WAND`、`AUTOINDEX` 等）
- **云上环境**（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`）：**必须使用 `AUTOINDEX`**，不能使用其他显式索引类型

**LLM 生成 JSON 时的建议**：

1. **根据环境选择索引类型**：
   - 如果用户指定了云上环境（通过 `-Denv` 参数或上下文推断）→ **强制使用 `AUTOINDEX`**
   - 如果用户指定了本地环境（`devops`、`fouram`）→ 可以使用 `HNSW`、`BIN_IVF_FLAT`、`SPARSE_WAND`、`STL_SORT` 等
   - 如果用户没指定环境 → 默认使用 `AUTOINDEX`（最安全的选择）

2. **向量字段索引类型选择**：
   - 如果用户指定了向量类型但没指定索引类型：
     - `FloatVector` / `Float16Vector` / `BFloat16Vector` / `Int8Vector` → 使用 `AUTOINDEX` + `L2`
     - `BinaryVector` → 使用 `AUTOINDEX` + `HAMMING`（**不能使用 L2**）
     - `SparseFloatVector` → 使用 `AUTOINDEX` + `IP`（**特殊情况**：如果是由 BM25 function 生成的，则使用 `BM25`；**不能使用 L2**）
     - Array of Struct 中的向量字段 → 使用 `HNSW` + `MAX_SIM_L2`（**必须建索引**，字段名格式：`<structFieldName>[<subFieldName>]`，按照 Array 规则使用方括号）
   - 如果用户指定了向量类型但没指定 MetricType：
     - 根据上表自动选择对应的 MetricType
     - **特殊情况**：对于 `SparseFloatVector`，需要检查 `CreateCollectionParams.functionParams`：
       - 如果 `functionParams.functionType == "BM25"` 且该 `SparseFloatVector` 字段名在 `functionParams.outputFieldNames` 中 → 使用 `BM25`
       - 否则 → 使用 `IP`
     - **Array of Struct 中的向量字段**：
       - **必须建索引**：Array of Struct 中的向量字段必须建索引，否则无法进行搜索
       - 字段名格式：`<structFieldName>[<subFieldName>]`（按照 Array 规则使用方括号），例如 `clips[clip_embedding]`
       - MetricType：必须使用 `MAX_SIM_L2`、`MAX_SIM_IP` 或 `MAX_SIM_COSINE`（**不能使用普通的 L2/IP/COSINE**）

3. **标量字段索引类型选择**：
   - 如果用户需要对标量字段建索引（用于加速查询/排序）：
     - `VarChar` / `String` / `Int64` / `Int32` / `Int16` / `Int8` / `Float` / `Double` / `Bool` → 使用 `STL_SORT` 或 `AUTOINDEX`
     - **注意**：标量字段**不需要 MetricType**（MetricType 仅用于向量字段）
   - JSON 字段索引：
     - 如果对 JSON 字段或 dynamic field 中的标量字段建索引，必须设置 `jsonPath` 和 `jsonCastType`
     - 示例：`{"fieldName": "json_field", "indextype": "STL_SORT", "jsonPath": "field[\"key1\"]", "jsonCastType": "varchar"}`

4. **常见错误**：
   - ❌ 云上环境使用 `HNSW` → 应该用 `AUTOINDEX`
   - ❌ `BinaryVector` 使用 `L2` → 应该用 `HAMMING`
   - ❌ `SparseFloatVector` 使用 `L2` → 应该用 `IP`（或 `BM25`，如果是由 BM25 function 生成的）
   - ❌ BM25 function 生成的 `SparseFloatVector` 使用 `IP` → 应该用 `BM25`
   - ❌ 本地环境使用 `buildLevel` → `buildLevel` 仅在云上环境支持
   - ❌ `BinaryVector` 或 `SparseFloatVector` 使用 `buildLevel` → `buildLevel` 仅支持 `FloatVector`、`Float16Vector`、`BFloat16Vector`
   - ❌ `BinaryVector` 使用 `HNSW` → 应该用 `BIN_IVF_FLAT`（或 `AUTOINDEX`）
   - ❌ 标量字段设置 `MetricType` → 标量字段不需要 MetricType
   - ❌ JSON 字段索引缺少 `jsonPath` 或 `jsonCastType` → JSON 字段索引必须指定路径和类型
   - ❌ Array of Struct 中的向量字段使用普通 `L2`/`IP`/`COSINE` → 应该用 `MAX_SIM_L2`/`MAX_SIM_IP`/`MAX_SIM_COSINE`
   - ❌ Array of Struct 字段名格式错误 → 应该用 `clips[clip_embedding]` 格式（按照 Array 规则使用方括号），而不是 `clips.clip_embedding` 或 `clips_clip_embedding`
   - ❌ Array of Struct 中的向量字段未建索引 → **必须建索引**，否则无法进行搜索

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
- **`FieldParams` 中的 boolean 字段建议显式给值**：`autoId`、`partitionKey`、`nullable`、`enableMatch`、`enableAnalyzer` 即使为 `false` 也建议显式写出，避免反序列化歧义。
- **`CreateCollectionParams` 的 `numPartitions` 与 `partitionKey` 约束**：如果 `numPartitions > 0`，必须至少有一个字段的 `partitionKey` 为 `true`；如果 `numPartitions = 0`，所有字段的 `partitionKey` 都应为 `false`。
- **索引类型与环境约束**：如果用户指定了云上环境（`awswest`、`gcpwest`、`azurewest`、`alihz`、`tcnj`、`hwc`），**必须使用 `AUTOINDEX`**；本地环境（`devops`、`fouram`）可以使用所有索引类型。
- 未指定 collection 时，`collectionName` 设为 `""`（空字符串），并尽量不要同时填 `collectionRule`。

你也可以让 LLM 一并产出：

- `initial_params`（建议至少 `{ "cleanCollection": false }`）
- `customize_params`（核心）

---

### 9. 根据简短需求自动生成完整流程（LLM 智能补全）

当用户给出简短需求（例如："我想跑一个collection的插入和搜索测试"）时，LLM 应该**自动补充必要的前置步骤**，生成完整的 `customize_params`。

#### 9.1 前置步骤依赖关系

Milvus 操作有严格的依赖顺序，LLM 生成 JSON 时必须遵循：

1. **CreateCollection**（创建 collection）
   - 必须先创建 collection，才能进行后续操作
   - 如果用户没有指定 collection schema，LLM 应该生成一个**最小可用 schema**（至少包含主键 + 向量字段）
   - **向量字段约束**：一个 collection 必须至少包含一个向量字段（可以是 Array of Struct 中的向量子字段），但在示例中建议提供一个正常的顶层向量字段（如 `FloatVector`）

2. **CreateIndex**（创建索引）
   - 向量字段必须建索引后才能被搜索
   - 如果用户提到"搜索"但没提到索引，LLM 应该自动添加 `CreateIndexParams`
   - 如果用户没有指定索引类型，使用默认：`AUTOINDEX` + `L2`

3. **Load**（加载 collection）
   - collection 必须加载到内存后才能进行 Insert/Search/Query/Upsert
   - 如果用户提到"插入"或"搜索"但没提到 Load，LLM 应该自动添加 `LoadParams`

4. **Flush**（可选，但建议在 Insert 后添加）
   - Flush 操作用于将 **growing segment** 刷成 **sealed segment**
   - Insert 后建议 Flush，确保数据落盘并触发后续的索引构建
   - 在并发读写测试中，Flush 应该在 Insert 之后执行（或与 Search/Query 并行）

5. **Insert/Search/Query/Upsert**（用户实际需求）
   - 这些操作必须在 Load 之后执行

#### 9.2 常见场景模板

##### 场景 1：插入和搜索测试（最常见）

**用户需求**："我想跑一个collection的插入和搜索测试"

**LLM 应该生成的完整流程**：

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id_pk",
        "primaryKey": true,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "FloatVector",
        "fieldName": "vec",
        "dim": 128,
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      }
    ],
    "functionParams": null,
    "properties": [],
    "databaseName": ""
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
  }
}
```

**关键点**：
- 自动补充了 `CreateCollectionParams_0`（最小 schema）
- 自动补充了 `CreateIndexParams_1`（向量字段索引）
- 自动补充了 `LoadParams_2`（加载 collection）
- 自动补充了 `FlushParams_4`（Insert 后 Flush）
- 用户实际需求：`InsertParams_3` 和 `SearchParams_5`

##### 场景 2：仅搜索测试（假设 collection 已存在）

**用户需求**："我想搜索一个collection"

**LLM 应该生成的完整流程**：

```json
{
  "LoadParams_0": {
    "loadAll": false,
    "collectionName": "",
    "loadFields": [],
    "skipLoadDynamicField": false
  },
  "SearchParams_1": {
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
  }
}
```

**关键点**：
- 如果用户明确说"已有 collection"或"collection 已存在"，则**不需要** `CreateCollectionParams` 和 `CreateIndexParams`
- 但仍需要 `LoadParams`（因为搜索前必须 Load）

##### 场景 3：创建 collection 并插入数据

**用户需求**："创建一个collection并插入10万条数据"

**LLM 应该生成的完整流程**：

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id_pk",
        "primaryKey": true,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "FloatVector",
        "fieldName": "vec",
        "dim": 128,
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      }
    ],
    "functionParams": null,
    "properties": [],
    "databaseName": ""
  },
  "LoadParams_1": {
    "loadAll": false,
    "collectionName": "",
    "loadFields": [],
    "skipLoadDynamicField": false
  },
  "InsertParams_2": {
    "collectionName": "",
    "collectionRule": "",
    "partitionName": "",
    "startId": 0,
    "numEntries": 100000,
    "batchSize": 1000,
    "numConcurrency": 5,
    "dataset": "random",
    "runningMinutes": 0,
    "retryAfterDeny": false,
    "ignoreError": false,
    "generalDataRoleList": []
  },
  "FlushParams_3": {
    "flushAll": false,
    "collectionName": ""
  }
}
```

**关键点**：
- 自动补充了 `CreateCollectionParams_0`
- 自动补充了 `LoadParams_1`（Insert 前必须 Load）
- 用户指定了 `numEntries: 100000`，LLM 应该使用该值
- 自动补充了 `FlushParams_3`（Insert 后 Flush）

##### 场景 4：性能测试（递增压力测试）

**用户需求**："我想测试 search/insert/query 的性能"

**性能测试最佳实践**：

当需要测试 search/insert/query 等操作的性能时，**推荐添加多个相同类型的组件，设置不同的并发数（`numConcurrency`），来递增压力**。这样可以：
- 观察不同并发级别下的性能表现
- 找到系统的性能瓶颈和最优并发数
- 评估系统的扩展性和稳定性

**LLM 应该生成的完整流程示例（Search 性能测试）**：

```json
{
  "LoadParams_0": {
    "loadAll": false,
    "collectionName": "",
    "loadFields": [],
    "skipLoadDynamicField": false
  },
  "SearchParams_1": {
    "collectionName": "",
    "collectionRule": "",
    "annsField": "vec",
    "nq": 1,
    "topK": 10,
    "outputs": ["*"],
    "filter": "",
    "numConcurrency": 1,
    "runningMinutes": 5,
    "randomVector": true,
    "searchLevel": 1,
    "indexAlgo": "",
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  },
  "SearchParams_2": {
    "collectionName": "",
    "collectionRule": "",
    "annsField": "vec",
    "nq": 1,
    "topK": 10,
    "outputs": ["*"],
    "filter": "",
    "numConcurrency": 5,
    "runningMinutes": 5,
    "randomVector": true,
    "searchLevel": 1,
    "indexAlgo": "",
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  },
  "SearchParams_3": {
    "collectionName": "",
    "collectionRule": "",
    "annsField": "vec",
    "nq": 1,
    "topK": 10,
    "outputs": ["*"],
    "filter": "",
    "numConcurrency": 10,
    "runningMinutes": 5,
    "randomVector": true,
    "searchLevel": 1,
    "indexAlgo": "",
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  },
  "SearchParams_4": {
    "collectionName": "",
    "collectionRule": "",
    "annsField": "vec",
    "nq": 1,
    "topK": 10,
    "outputs": ["*"],
    "filter": "",
    "numConcurrency": 20,
    "runningMinutes": 5,
    "randomVector": true,
    "searchLevel": 1,
    "indexAlgo": "",
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  }
}
```

**关键点**：
- 添加了多个 `SearchParams` 组件（`SearchParams_1`、`SearchParams_2`、`SearchParams_3`、`SearchParams_4`）
- 每个组件的 `numConcurrency` 递增（1 → 5 → 10 → 20），实现递增压力测试
- 其他参数（`nq`、`topK`、`runningMinutes` 等）保持一致，确保测试结果可比较
- 同样的方法也适用于 `InsertParams`、`QueryParams` 和 `UpsertParams` 的性能测试

**性能测试建议**：
- **并发数递增策略**：建议从较小的并发数开始（如 1、5），逐步增加到较大的值（如 10、20、50、100），观察性能变化
- **测试时长**：每个并发级别的 `runningMinutes` 建议设置为相同值（如 5-10 分钟），确保测试结果可比较
- **错误处理**：性能测试时建议设置 `ignoreError: true`，避免单个错误中断整个测试流程
- **数据准备**：确保 collection 中已有足够的数据量，避免因数据不足影响性能测试结果

##### 场景 5：批量创建多个 collection 并插入数据

**用户需求**："创建 50 个 collection，每个 collection 插入 1000 条数据"

**重要提示**：当需要批量创建多个 collection 并插入数据时，**必须使用 `LoopParams` 组件来循环执行**，而不是生成多个重复的 `CreateCollectionParams` 和 `InsertParams` 组件。

**LLM 应该生成的完整流程**：

```json
{
  "LoopParams_0": {
    "paramComb": {
      "CreateCollectionParams_0": {
        "collectionName": "",
        "shardNum": 1,
        "numPartitions": 0,
        "enableDynamic": false,
        "fieldParamsList": [
          {
            "dataType": "Int64",
            "fieldName": "id_pk",
            "primaryKey": true,
            "autoId": false,
            "partitionKey": false,
            "nullable": false,
            "enableMatch": false,
            "enableAnalyzer": false,
            "analyzerParamsList": []
          },
          {
            "dataType": "FloatVector",
            "fieldName": "vec",
            "dim": 128,
            "primaryKey": false,
            "autoId": false,
            "partitionKey": false,
            "nullable": false,
            "enableMatch": false,
            "enableAnalyzer": false,
            "analyzerParamsList": []
          }
        ],
        "functionParams": null,
        "properties": [],
        "databaseName": ""
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
        "numEntries": 1000,
        "batchSize": 1000,
        "numConcurrency": 1,
        "dataset": "random",
        "runningMinutes": 0,
        "retryAfterDeny": false,
        "ignoreError": false,
        "generalDataRoleList": []
      },
      "FlushParams_4": {
        "flushAll": false,
        "collectionName": ""
      }
    },
    "runningMinutes": 0,
    "cycleNum": 50
  }
}
```

**关键点**：
- 使用 `LoopParams_0` 作为外层组件，设置 `cycleNum: 50` 来循环执行 50 次
- `paramComb` 内部包含完整的操作序列：`CreateCollectionParams_0` → `CreateIndexParams_1` → `LoadParams_2` → `InsertParams_3` → `FlushParams_4`
- **Collection 名称管理**：
  - `CreateCollectionParams` 的 `collectionName: ""`（空字符串）表示**自动生成随机名称**，每次循环都会创建一个新的 collection
  - 所有创建的 collection 名称会被记录到 `globalCollectionNames` 列表中
- **后续组件使用 collectionRule 选择 collection**：
  - 在 Loop 内部的 `InsertParams`、`SearchParams`、`QueryParams` 等组件中，可以设置 `collectionName: ""` 和 `collectionRule: "sequence"` 或 `collectionRule: "random"` 来选择 collection
  - `collectionRule: "sequence"`：按顺序轮转选择 `globalCollectionNames` 中的 collection（使用 `insertCollectionIndex`、`searchCollectionIndex` 等索引）
  - `collectionRule: "random"`：从 `globalCollectionNames` 中随机选择一个 collection
  - `collectionRule: ""`（空字符串）：默认使用 `globalCollectionNames` 的最后一个 collection
- **不要**生成 50 个 `CreateCollectionParams_0`、`CreateCollectionParams_1`...`CreateCollectionParams_49` 这样的重复组件

**使用 Loop 的优势**：
- **配置简洁**：只需要一个 `LoopParams` 组件，而不是大量重复的组件
- **易于维护**：修改操作序列时只需要修改 `paramComb` 内部的内容
- **自动管理 collection 名称**：每次循环会自动生成新的 collection 名称（通过 `globalCollectionNames` 机制）
- **灵活选择 collection**：后续组件可以通过 `collectionRule` 灵活选择已创建的 collection（顺序或随机）
- **资源高效**：避免生成大量重复的 JSON 配置

**示例：Loop 外部使用 collectionRule 选择 collection**

如果需要在 Loop 外部对已创建的 collection 进行操作，可以使用 `collectionRule`：

```json
{
  "LoopParams_0": {
    "paramComb": {
      "CreateCollectionParams_0": { ... },
      "InsertParams_1": { ... }
    },
    "cycleNum": 50
  },
  "SearchParams_1": {
    "collectionName": "",
    "collectionRule": "sequence",
    "annsField": "vec",
    "nq": 1,
    "topK": 10,
    ...
  },
  "SearchParams_2": {
    "collectionName": "",
    "collectionRule": "random",
    "annsField": "vec",
    "nq": 1,
    "topK": 10,
    ...
  }
}
```

这样，`SearchParams_1` 会按顺序轮转选择 50 个 collection，`SearchParams_2` 会随机选择其中一个 collection。

#### 9.3 LLM 智能补全规则（给 LLM 的 Prompt 建议）

当用户给出简短需求时，LLM 应该遵循以下规则：

1. **识别用户意图**：
   - "插入" → 需要 `InsertParams`
   - "搜索" → 需要 `SearchParams` + `CreateIndexParams`（如果 collection 未建索引）
   - "查询" → 需要 `QueryParams`
   - "创建 collection" → 需要 `CreateCollectionParams`

2. **自动补充前置步骤**：
   - 如果提到"搜索"但没提到"索引"或"collection 已存在" → 自动添加 `CreateIndexParams`
   - 如果提到"插入/搜索/查询"但没提到"Load"或"collection 已加载" → 自动添加 `LoadParams`
   - 如果提到"插入/搜索/查询"但没提到 collection schema → 自动添加 `CreateCollectionParams`（最小 schema）

3. **识别批量操作需求，使用 Loop 组件**：
   - 如果用户提到"创建 N 个 collection"（N > 1）或"批量创建" → **必须使用 `LoopParams` 组件**，设置 `cycleNum: N`，而不是生成 N 个重复的 `CreateCollectionParams` 组件
   - 如果用户提到"每个 collection 插入 X 条数据"且涉及多个 collection → 将 `CreateCollectionParams`、`CreateIndexParams`、`LoadParams`、`InsertParams` 等操作放在 `LoopParams` 的 `paramComb` 内部
   - **错误示例**：生成 `CreateCollectionParams_0`、`CreateCollectionParams_1`...`CreateCollectionParams_49`（50 个重复组件）
   - **正确示例**：使用 `LoopParams_0`，设置 `cycleNum: 50`，`paramComb` 内部包含 `CreateCollectionParams_0`、`InsertParams_1` 等

4. **最小可用 schema 模板**：
   - 主键字段：`Int64`，`fieldName: "id_pk"`，`primaryKey: true`，`autoId: false`
   - 向量字段：`FloatVector`，`fieldName: "vec"`，`dim: 128`（如果用户没指定维度）
   - 所有 boolean 字段显式给 `false`

5. **序号分配**：
   - 从 `_0` 开始递增
   - 前置步骤的序号应该小于用户实际需求的序号

5. **字段推断**：
   - 如果用户没指定 `annsField`，使用 schema 中的第一个向量字段名（通常是 `vec`）
   - 如果用户没指定 `numEntries`，使用默认值 `10000`
   - 如果用户没指定 `runningMinutes`，Insert 用 `0`（按数据量），Search/Query 用 `1`（按时间）

#### 9.4 示例：从简短需求到完整 JSON

**用户输入**："我想跑一个collection的插入和搜索测试，插入5万条数据，搜索运行2分钟"

**LLM 应该输出**：

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id_pk",
        "primaryKey": true,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "FloatVector",
        "fieldName": "vec",
        "dim": 128,
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      }
    ],
    "functionParams": null,
    "properties": [],
    "databaseName": ""
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
    "numEntries": 50000,
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
    "runningMinutes": 2,
    "randomVector": true,
    "searchLevel": 1,
    "indexAlgo": "",
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  }
}
```

**关键点**：
- ✅ 自动补充了前置步骤（CreateCollection、CreateIndex、Load、Flush）
- ✅ 使用了用户指定的 `numEntries: 50000`
- ✅ 使用了用户指定的 `runningMinutes: 2`（Search）
- ✅ 保持了正确的执行顺序（序号递增）

---

### 10. 一个“最小可用”示例（建议让 LLM 从它改）

> 注意：仓库内 `src/main/resources/example/base.json` 等示例文件会随代码演进调整；如果你要喂给 LLM，请优先以本文给出的字段名/必填字段/枚举取值为准。

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id_pk",
        "primaryKey": true,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "VarChar",
        "fieldName": "varchar_1",
        "maxLength": 256,
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "FloatVector",
        "fieldName": "vec",
        "dim": 128,
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      }
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


