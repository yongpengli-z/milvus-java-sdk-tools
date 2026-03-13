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

6. **Partition（分区管理，可选）**
   - 分区操作需要 collection 已经存在
   - `CreatePartitionParams` / `DropPartitionParams` / `HasPartitionParams` / `ListPartitionsParams`：在 CreateCollection 之后使用
   - `LoadPartitionsParams`：加载指定分区（替代 `LoadParams` 的全量加载），在 CreateIndex 之后使用
   - `ReleasePartitionsParams`：释放指定分区（替代 `ReleaseParams` 的全量释放）
   - **与 `numPartitions`（Partition Key）的区别**：`CreatePartitionParams` 用于手动创建命名分区；`CreateCollectionParams.numPartitions` 是 Partition Key 的自动分区机制，两者是不同的分区方式
   - **按分区插入/搜索场景**：先 `CreatePartitionParams` 创建分区，再 `LoadPartitionsParams` 加载分区，然后 `InsertParams`（`partitionName` 指定分区名）/ `SearchParams`

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
    "fieldDataSourceList": [],
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
    "fieldDataSourceList": [],
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
        "fieldDataSourceList": [],
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
   - "创建分区" / "按分区" → 需要 `CreatePartitionParams`（前置：collection 已存在）
   - "加载分区" → 需要 `LoadPartitionsParams`（而非 `LoadParams`）
   - "释放分区" → 需要 `ReleasePartitionsParams`（而非 `ReleaseParams`）
   - "列出分区" / "查看分区" → 需要 `ListPartitionsParams`
   - "检查分区" / "分区是否存在" → 需要 `HasPartitionParams`
   - "列出 collection" / "查看 collection 列表" → 需要 `ListCollectionsParams`
   - "collection 是否存在" → 需要 `HasCollectionParams`
   - "加载状态" / "是否已加载" → 需要 `GetLoadStateParams`
   - "查看索引" / "索引信息" → 需要 `DescribeIndexParams`
   - "列出索引" / "索引列表" → 需要 `ListIndexesParams`
   - "删除别名" → 需要 `DropAliasParams`
   - "列出别名" / "查看别名" → 需要 `ListAliasesParams`
   - "别名详情" → 需要 `DescribeAliasParams`
   - "按 ID 获取" / "get" → 需要 `GetParams`
   - "遍历数据" / "迭代查询" → 需要 `QueryIteratorParams`

2. **自动补充前置步骤**：
   - 如果提到"搜索"但没提到"索引"或"collection 已存在" → 自动添加 `CreateIndexParams`
   - 如果提到"插入/搜索/查询"但没提到"Load"或"collection 已加载" → 自动添加 `LoadParams`（如果是按分区操作，则使用 `LoadPartitionsParams`）
   - 如果提到"插入/搜索/查询"但没提到 collection schema → 自动添加 `CreateCollectionParams`（最小 schema）
   - 如果提到"按分区插入"或指定了 `partitionName` → 自动添加 `CreatePartitionParams`（如果分区不存在）

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

6. **字段推断**：
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
    "fieldDataSourceList": [],
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

