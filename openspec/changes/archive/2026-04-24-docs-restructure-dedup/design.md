## Context

当前 `docs/` 目录有 16 个 Markdown 文件（3881 行，171K 字符），约 26% 内容重复。文档目标受众是 LLM，用于根据用户自然语言描述生成 `customize_params` JSON 配置。项目共有约 60 个独立组件（对应 `custom.entity.*Params` 类）。

## Goals / Non-Goals

**Goals:**
- README 作为 AI 唯一入口：读完 README 即可知道用哪些组件、如何构造 JSON
- 每个组件一个文件：全量参数 + 约束 + 注意事项，自包含不依赖其他组件文件
- 零重复：每个知识点只在一处定义
- 维护便捷：改一个组件只动一个文件

**Non-Goals:**
- 不改动任何 Java 代码
- 不改变组件的参数结构或行为
- 不创建面向人类开发者的单独文档

## Decisions

### 1. README 作为"总控 prompt"

**选择**: 将 README.md 改造为 AI 读取的第一个（也可能是唯一一个）文件，包含：
1. 组件功能索引表（~60 行表格）
2. 操作依赖顺序
3. JSON 构造规则
4. 智能补全规则
5. 输出格式要求
6. 场景模板

**原因**: AI 读完 README 就能决定使用哪些组件并生成正确格式的 JSON。只有需要查某个组件的具体参数时，才去读对应的组件文件。
**预估**: README 约 800-1000 行（~15K token），比现在的 `llm-prompt.md`（834 行）差不多，但信息密度更高、无重复。

### 2. 每组件一文件，放在 `docs/components/` 下

**选择**: `docs/components/CreateCollectionParams.md`、`docs/components/SearchParams.md` 等，文件名与 Java 类名一致。

**原因**:
- 文件名 = 类名 = JSON key 前缀，AI 可以直接推断文件路径
- 改一个组件只动一个文件
- 每个文件 30-80 行（~500-1500 token），AI 按需读取

**替代方案**: 按类别分子目录（如 `docs/components/collection/`、`docs/components/data/`）→ 增加路径复杂度，无实际收益。

### 3. 子结构（FieldParams、IndexParams 等）内嵌在父组件文件中

**选择**: FieldParams 的说明写在 `CreateCollectionParams.md` 内，IndexParams 写在 `CreateIndexParams.md` 内。

**原因**: 子结构不会被独立引用（它们只在父组件的 List 字段中使用），独立成文件反而增加理解成本。

### 4. 枚举约束内嵌在 README 和相关组件文件中

**选择**:
- README 中放 DataType 枚举列表（一行）和 IndexType/MetricType 约束矩阵（一个表格）
- 各组件文件中放该组件特有的约束

**原因**: 枚举值是全局共享知识，放 README 避免重复；组件级约束（如 "QueryParams 的 filter 和 ids 必须至少传一个"）放在组件文件中，保持自包含。

### 5. 不再维护 `llm-prompt.md`

**选择**: 删除 `llm-prompt.md`，其内容拆分到 README（规则部分）和各组件文件（参数部分）。

**原因**: `llm-prompt.md` 是重复的根源——它把所有组件参数再抄一遍。

## Risks / Trade-offs

- **[README 较长]** README 约 800-1000 行 → 但 AI 只需读一次，且信息密度高于现有方案
- **[复杂场景需多次读取]** 用户说"搜索"，AI 可能需要读 4-6 个组件文件 → README 中的场景模板已包含常用组合的完整示例，大多数场景不需要逐个读组件文件
- **[文件数量多]** 60 个组件文件 → 但每个文件短小（30-80 行），命名规则明确，AI 可直接推断路径
