## ADDED Requirements

### Requirement: README 作为 AI 入口文档
`README.md` SHALL 包含以下内容，使 AI 读完后能独立决定使用哪些组件并生成正确格式的 JSON：

1. **项目定位**：一段话说明这是"参数驱动的 Milvus 测试/压测执行器"
2. **启动参数**：`-Duri/-Dtoken/-Denv/-DtaskId/-Dinitial_params/-Dcustomize_params` 说明
3. **三种实例获取方式**：已有实例（传 uri）、管控创建（CreateInstanceParams）、Helm 部署（HelmCreateInstanceParams）
4. **组件功能索引表**：所有独立组件的表格，每行包含 `组件名 | 一句话功能说明 | 文件链接`，按类别分组（Collection/索引/加载 → 数据操作 → 高级搜索 → 管理 → 分区 → Database/Alias → 流程控制 → 实例管理 → Helm）
5. **操作依赖顺序**：CreateCollection → CreateIndex → Load → Insert → Flush → Search/Query 的依赖链
6. **JSON 构造规则**：key 命名 `<ParamsClassName>_<序号>`、排序执行、List 字段必须 `[]`、全局状态（globalCollectionNames/collectionRule）
7. **枚举速查**：DataType 枚举列表、IndexType/MetricType 约束矩阵（含部署方式约束）、env 枚举
8. **LLM 智能补全规则**：意图识别、自动补充前置步骤、最小 schema 模板、序号分配、字段推断
9. **JSON 输出格式要求**：可省略/不可省略字段清单、紧凑格式
10. **常见场景模板**：插入+搜索、仅搜索、创建+插入、性能测试、批量创建（LoopParams）的完整 JSON 示例

#### Scenario: AI 根据用户需求选择组件
- **WHEN** AI 收到"跑一个搜索测试"的需求
- **THEN** 读完 README 后，通过组件索引表和智能补全规则，确定需要 CreateCollectionParams → CreateIndexParams → LoadParams → InsertParams → FlushParams → SearchParams

#### Scenario: AI 生成 JSON 格式正确
- **WHEN** AI 需要生成 customize_params JSON
- **THEN** 按 README 中的 JSON 构造规则生成，key 命名、List 字段、枚举值均正确

### Requirement: 每个独立组件一个 .md 文件
每个独立组件（对应 `custom.entity.*Params` 且有对应 `*Comp` 的组件）SHALL 在 `docs/components/` 下有一个同名 .md 文件，文件 SHALL 自包含以下内容：

1. **组件用途**：一句话说明
2. **全量参数字段表**：字段名、类型、是否必填、默认值、说明
3. **该组件特有的约束和限制**
4. **注意事项和易踩坑点**
5. **JSON 示例**（至少一个最小可用示例）
6. **子结构说明**：如 CreateCollectionParams 包含 FieldParams/FunctionParams，在同一文件内说明

文件 SHALL NOT 依赖其他组件文件的内容（自包含）。枚举值和全局规则可引用 README。

#### Scenario: 查阅 CreateCollectionParams 参数
- **WHEN** AI 需要知道 CreateCollectionParams 的 FieldParams 中 analyzerParamsList 怎么填
- **THEN** 在 `docs/components/CreateCollectionParams.md` 中找到完整说明和正确/错误示例

#### Scenario: 修改 SearchParams 新增参数
- **WHEN** 开发者为 SearchParams 新增一个字段
- **THEN** 只需修改 `docs/components/SearchParams.md` 一个文件

### Requirement: 组件文件命名与 Java 类名一致
组件文件 SHALL 使用 `<ParamsClassName>.md` 命名（如 `CreateCollectionParams.md`、`SearchParams.md`），与 Java 类名和 JSON key 前缀一致。

#### Scenario: AI 推断组件文件路径
- **WHEN** AI 需要查阅 `HybridSearchParams` 的详细参数
- **THEN** 直接访问 `docs/components/HybridSearchParams.md`

### Requirement: 子结构不单独建文件
`FieldParams`、`IndexParams`、`StructFieldParams`、`FunctionParams`、`InitialParams`、`StreamingNodeParams` 等子结构 SHALL 内嵌在父组件文件中，SHALL NOT 单独建文件。

#### Scenario: FieldParams 只在 CreateCollectionParams 中说明
- **WHEN** AI 需要了解 FieldParams 的 enableAnalyzer 字段
- **THEN** 在 `docs/components/CreateCollectionParams.md` 中找到，不存在单独的 `FieldParams.md`

### Requirement: 无内容重复
README 和所有组件文件之间 SHALL NOT 存在重复定义同一知识点的情况。
- 全局规则（JSON 构造规则、枚举值、操作依赖顺序）只在 README 中定义
- 组件级参数和约束只在对应组件文件中定义
- README 中的场景模板使用精简字段（可省略默认值字段），组件文件中有全量字段说明

#### Scenario: 修改 DataType 枚举
- **WHEN** 新增一个 DataType
- **THEN** 只修改 README 中的枚举速查表，组件文件无需修改

### Requirement: 删除所有旧文档
`docs/` 目录下现有的 16 个 .md 文件 SHALL 全部删除，被 `docs/components/*.md` 替代。

#### Scenario: 旧文件不再存在
- **WHEN** 文档重组完成后
- **THEN** `docs/llm-prompt.md`、`docs/params-overview.md` 等旧文件不存在，`docs/` 下只有 `components/` 子目录
