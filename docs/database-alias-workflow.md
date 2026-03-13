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

##### 5.4.4 DropAlias：`DropAliasParams`

对应组件：`custom.components.DropAliasComp`

**用途**：删除指定的别名。

字段：

- **`databaseName`**（string，可空）：前端默认 `""`。
- **`alias`**（string，必填）：要删除的别名。

##### 5.4.5 ListAliases：`ListAliasesParams`

对应组件：`custom.components.ListAliasesComp`

**用途**：列出指定 collection 的所有别名。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`databaseName`**（string，可空）：前端默认 `""`。

##### 5.4.6 DescribeAlias：`DescribeAliasParams`

对应组件：`custom.components.DescribeAliasComp`

**用途**：查看别名的详细信息（对应的 collection、database 等）。

字段：

- **`databaseName`**（string，可空）：前端默认 `""`。
- **`alias`**（string，必填）：要查看的别名。

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

##### 5.5.4 DebugTest（内部调试）：`DebugTestParams`

对应组件：`custom.components.DebugTestComp`

**用途**：内部调试/测试用组件，不在前端 customize 面板中暴露。用于开发阶段的功能验证。

字段：

- **`test`**（string）：测试名称/标识符，`DebugTestComp` 会根据该值决定执行哪种调试逻辑。

> 注意：该组件仅供内部调试使用，普通用户/LLM 生成 JSON 时通常不需要使用。

---

