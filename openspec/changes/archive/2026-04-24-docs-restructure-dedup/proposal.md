## Why

现有 `docs/` 目录下有 16 个文档文件，内容重复严重（重复率约 26%）。`llm-prompt.md`（834行）几乎是其他所有文档的压缩合集，与 `params-overview.md`、`minimal-example.md`、`llm-rules-and-examples.md`、各组件文档之间存在大量重复。维护成本高——任何字段变更需同步修改多处，容易遗漏导致文档不一致。

## What Changes

采用**"README 做入口 + 每组件一文件"**的方案：

- **改造 `README.md`** 为 AI 入口文档，包含：
  - 项目定位与启动参数
  - 所有组件的功能索引表（组件名 → 一句话功能说明 → 链接到详情文件）
  - 操作依赖顺序（CreateCollection → CreateIndex → Load → Insert → Search）
  - `customize_params` JSON 构造规则（key 命名、排序执行、List 必须给 `[]`、全局状态）
  - LLM 智能补全规则（意图识别、自动补充前置步骤）
  - JSON 输出格式要求（可省略/不可省略字段、紧凑格式）
  - 常见场景模板（插入+搜索、性能测试、批量创建等）

- **删除现有 `docs/` 下所有 16 个 .md 文件**

- **创建 `docs/components/` 目录**，每个独立组件一个 .md 文件（约 60 个），每个文件自包含：
  - 该组件的全量参数字段说明
  - 该组件特有的约束和限制
  - 该组件的注意事项和易踩坑点
  - 该组件的 JSON 示例
  - 如果有子结构（如 CreateCollectionParams 的 FieldParams），在同一文件内说明

## Capabilities

### New Capabilities
- `docs-restructure`: 将 16 个有重复内容的文档重组为 README（AI 入口）+ 60 个独立组件文件（无重复、自包含）

### Modified Capabilities

（无现有 spec 需要修改）

## Impact

- `docs/` 目录下所有现有 .md 文件将被删除，替换为 `docs/components/*.md`
- `README.md` 将从简短项目介绍改造为 AI 完整入口文档
- 对项目代码无影响（纯文档变更）
- 使用这些文档作为 LLM prompt 的下游系统需更新文件引用路径
