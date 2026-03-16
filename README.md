# milvus-java-sdk-toos

**参数驱动的 Milvus 测试/压测执行器**

- **输入**：前端传入的 JSON（`customize_params`），以及启动参数（`uri/token/env/taskId/initial_params`）
- **执行**：程序按规则解析 JSON 并反序列化为 `custom.entity.*Params` 对象，由 `custom.common.ComponentSchedule` 调度到 `custom.components.*Comp` 执行

## 项目结构

```
milvus-java-sdk-toos/
├── pom.xml                          # Maven 项目配置
├── README.md                        # 项目简介 + 文档索引
├── docs/                            # 详细参考文档（拆分章节）
├── src/main/java/custom/
│   ├── BaseTest.java                # 主程序入口
│   ├── common/ComponentSchedule.java # customize_params 解析与执行编排
│   ├── components/                  # 功能实现组件 (60个)
│   ├── entity/                      # 参数实体 (65个 Params)
│   └── utils/                       # 工具类库
├── ci/                              # Jenkins CI + Docker + K8s 配置
├── azure-aks-helm/                  # AKS Helm 部署方案
└── azure-docker-compose/            # Docker Compose 部署方案
```

## 文档索引

详细文档按主题拆分在 [`docs/`](docs/) 目录下：

### 基础概念

| 文档 | 内容 |
|------|------|
| [params-overview.md](docs/params-overview.md) | 项目定位、启动参数、`customize_params` 构造规则（顶层 key 命名、约束、全局状态） |
| [enums-reference.md](docs/enums-reference.md) | env、DataType、IndexType、MetricType、fieldDataSourceList 数据集 |
| [general-data-role.md](docs/general-data-role.md) | GeneralDataRole、generalFilterRoleList、$占位符 |
| [minimal-example.md](docs/minimal-example.md) | 最小可用示例（建表 → 建索引 → 加载 → 插入 → 搜索） |
| [llm-rules-and-examples.md](docs/llm-rules-and-examples.md) | LLM 输出硬约束、前置步骤依赖、5 种常见场景模板 |

### Collection 与索引

| 文档 | 包含组件 |
|------|----------|
| [collection-components.md](docs/collection-components.md) | `CreateCollectionParams` — 创建 Collection（含 FieldParams、Array of Struct、FunctionParams/BM25） |
| [index-load-components.md](docs/index-load-components.md) | `CreateIndexParams` — 创建索引（向量/标量/JSON/Struct 索引类型与 MetricType 约束）<br>`LoadParams` — 加载 Collection |

### 数据操作（CRUD）

| 文档 | 包含组件 |
|------|----------|
| [data-components.md](docs/data-components.md) | `InsertParams` — 插入数据（支持 fieldDataSourceList 数据集）<br>`SearchParams` — 向量搜索<br>`QueryParams` — 标量查询（filter / ids）<br>`UpsertParams` — 更新插入<br>`DeleteParams` — 删除数据（filter / ids）<br>`FlushParams` — Flush 落盘 |

### 迭代器与混合搜索

| 文档 | 包含组件 |
|------|----------|
| [iterator-hybridsearch.md](docs/iterator-hybridsearch.md) | `SearchIteratorParams` — 搜索迭代器<br>`QueryIteratorParams` — 查询迭代器<br>`HybridSearchParams` — 多向量混合搜索（RRF / WeightedRanker）<br>`RecallParams` — 召回率测试<br>`QuerySegmentInfoParams` — 查询 Segment 信息（V1）<br>`PersistentSegmentInfoParams` — 持久 Segment 信息（V1）<br>`BulkImportParams` — 批量导入（开发中） |

### 管理组件

| 文档 | 包含组件 |
|------|----------|
| [management-components.md](docs/management-components.md) | `ReleaseParams` — 释放 Collection<br>`DropCollectionParams` — 删除 Collection<br>`DropIndexParams` — 删除索引<br>`DescribeIndexParams` — 查看索引信息<br>`ListIndexesParams` — 列出索引<br>`CompactParams` — Compact 压缩<br>`ListCollectionsParams` — 列出 Collection<br>`HasCollectionParams` — 检查 Collection 是否存在<br>`GetLoadStateParams` — 查看加载状态<br>`GetParams` — 按 ID 获取实体 |

### 分区与 Schema 变更

| 文档 | 包含组件 |
|------|----------|
| [partition-schema-components.md](docs/partition-schema-components.md) | `CreatePartitionParams` — 创建分区<br>`DropPartitionParams` — 删除分区<br>`ListPartitionsParams` — 列出分区<br>`HasPartitionParams` — 检查分区是否存在<br>`LoadPartitionsParams` — 加载指定分区<br>`ReleasePartitionsParams` — 释放指定分区<br>`AddCollectionFieldParams` — 新增字段<br>`RenameCollectionParams` — 重命名 Collection<br>`DescribeCollectionParams` — 查看 Collection 详情 |

### Database / Alias / 流程控制

| 文档 | 包含组件 |
|------|----------|
| [database-alias-workflow.md](docs/database-alias-workflow.md) | `CreateDatabaseParams` — 创建 Database<br>`UseDatabaseParams` — 切换 Database<br>`CreateAliasParams` — 创建别名<br>`AlterAliasParams` — 修改别名<br>`DropAliasParams` — 删除别名<br>`ListAliasesParams` — 列出别名<br>`DescribeAliasParams` — 查看别名详情<br>`WaitParams` — 等待（按分钟）<br>`LoopParams` — 循环执行（paramComb + cycleNum）<br>`ConcurrentParams` — 并发执行（paramComb 内步骤并行）<br>`DebugTestParams` — 内部调试 |

### 实例管理（Cloud / Helm）

| 文档 | 包含组件 |
|------|----------|
| [cloud-components.md](docs/cloud-components.md) | `CreateInstanceParams` — 管控创建实例（cuType / dbVersion / replica）<br>`DeleteInstanceParams` — 删除实例<br>`StopInstanceParams` — 停止实例<br>`ResumeInstanceParams` — 恢复实例<br>`RestartInstanceParams` — 重启实例<br>`ScaleInstanceParams` — 升降配（CU / replica）<br>`RollingUpgradeParams` — 滚动升级<br>`ModifyParams` — 修改运行参数<br>`UpdateIndexPoolParams` — 更新索引池<br>`AlterInstanceIndexClusterParams` — 修改索引集群<br>`RestoreBackupParams` — 恢复备份 |
| [helm-components.md](docs/helm-components.md) | `HelmCreateInstanceParams` — Helm 部署实例（standalone / cluster / Woodpecker）<br>`HelmDeleteInstanceParams` — Helm 卸载实例 |

### 部署方案

| 文档 | 内容 |
|------|------|
| [azure-deploy.md](docs/azure-deploy.md) | AKS + Workload Identity、Docker Compose 部署方案 |
