# AGENTS.md — milvus-java-sdk-tools

## 项目定位

参数驱动的 Milvus 测试/压测执行器。输入为 JSON（`customize_params`），主入口 `custom.BaseTest`，由 `custom.common.ComponentSchedule` 调度到 `custom.components.*Comp` 执行。通过 QTP（testplatform）以 customize task 形式运行（argo pod），也可本地 `-D` 参数直接跑。

## 仓库约定（AI agent 必读）

1. **组件文档是唯一字段 source of truth**：构造/修改 params JSON 前，必须先读 `README.md`（全局规则）+ `docs/components/<ComponentName>.md`（字段定义）。严禁凭记忆或从 README 示例复制字段名。
2. **新增/修改参数的"三处同步"规则**：
   - `src/main/java/custom/entity/*Params.java`（后端字段）
   - `docs/components/<ComponentName>.md`（文档）
   - `test-platform-web/src/views/run/customize/components/items/<组件>Edit.vue`（前端表单，含默认值和旧配置兼容兜底 `ensureXxx()`）
3. **提交信息风格**：`feat(scope): 中文描述` / `fix(scope): ...` / `chore: ...`，参考 git log。
4. **编译验证**：改动后必须 `mvn -q compile -DskipTests` 通过。

## 关键实现细节（踩过的坑）

- **passRate 口径**：SearchComp 的 passRate = "返回结果数 == 期望数 的请求占比"，不是请求成功率。BM25/稀疏搜索返回不满 topK 属正常。期望数：普通 search = topK；group-by strict（groupByField 非空 + groupSize>1 + strictGroupSize=true）= topK*groupSize（topK 是组数，每组严格 groupSize 条）。
- **Text 数据类型仅 Milvus 3.0+ 支持**；2.6 实例用 VarChar + maxLength + enableAnalyzer 走 BM25。
- **云上实例（CreateInstanceParams 创建）所有索引必须 AUTOINDEX**；explicit 索引类型（HNSW 等）仅 Helm/本地环境可用。
- FieldParams 的 boolean 字段（primaryKey/autoId/partitionKey/nullable/enableMatch/enableAnalyzer）即使 false 也建议显式给。
- `enableMatch: true` 必须同时 `enableAnalyzer: true`；BM25 function 的 inputField 必须 `enableAnalyzer: true`。
- SDK 请求超时：Search/HybridSearch 通过 `client.withTimeout(timeoutMs).withRetry(maxRetryTimes=1)` 实现，`timeout<=0` 时用默认值（Search=800ms，HybridSearch=3000ms）。
- **严禁把本机软链/绝对路径文件提交入库**（2026-09 事故）：`.cursor/skills/` 下 127 个指向 `/Users/yongpengli/...` 的绝对路径软链被 dcbb2f0 误提交，Linux 克隆后为断链，导致 argo git artifact init 阶段 go-git checkout 报 `worktree contains unstaged changes`（exit 64），tcbj 定时任务全部失败。已在 a19e2ce 移除并加 `.cursor/skills/` 到 .gitignore。提交前用 `git ls-files -s | awk '$1==120000'` 检查无 symlink。

## 与 milvus-auto-test 工作流的关系

本仓库是 AI 自动化测试闭环的执行器。工作流 skill 在 testplatform 仓库 `skills/milvus-auto-test/`（本地 symlink 到 `~/.kimi/skills/milvus-auto-test`）：

- 场景规划/诊断规则/台账结构 → 看 skill 的 references
- 跑 case 发现脚本不支持 → 在本仓库开发新能力（遵循"三处同步"），commit 链接回写台账 `script_commits`
- 结果判定升级（verdict/校验）是本仓库的重点演进方向
