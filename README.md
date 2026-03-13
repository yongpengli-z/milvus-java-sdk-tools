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

| 文档 | 内容 |
|------|------|
| [params-overview.md](docs/params-overview.md) | 项目定位、启动参数、`customize_params` 构造规则（顶层 key 命名、约束、全局状态） |
| [collection-components.md](docs/collection-components.md) | CreateCollectionParams、FieldParams、Array of Struct 示例 |
| [index-load-components.md](docs/index-load-components.md) | CreateIndexParams、LoadParams、向量/标量索引类型与 MetricType 约束 |
| [data-components.md](docs/data-components.md) | InsertParams、SearchParams、QueryParams、UpsertParams、DeleteParams、FlushParams |
| [iterator-hybridsearch.md](docs/iterator-hybridsearch.md) | SearchIteratorParams、QueryIteratorParams、HybridSearchParams、RecallParams、SegmentInfo |
| [management-components.md](docs/management-components.md) | Release、Drop、Describe、List、Compact、Get 等管理组件 |
| [partition-schema-components.md](docs/partition-schema-components.md) | 分区 CRUD、AddCollectionField、RenameCollection、DescribeCollection |
| [database-alias-workflow.md](docs/database-alias-workflow.md) | Database/Alias 管理、WaitParams、LoopParams、ConcurrentParams |
| [cloud-components.md](docs/cloud-components.md) | CreateInstanceParams、Scale、RollingUpgrade、Modify、Stop/Resume/Restart |
| [helm-components.md](docs/helm-components.md) | HelmCreateInstanceParams、HelmDeleteInstanceParams、Woodpecker |
| [azure-deploy.md](docs/azure-deploy.md) | AKS + Workload Identity、Docker Compose 部署方案 |
| [enums-reference.md](docs/enums-reference.md) | env、DataType、IndexType、MetricType、fieldDataSourceList 数据集 |
| [general-data-role.md](docs/general-data-role.md) | GeneralDataRole、generalFilterRoleList、$占位符 |
| [llm-rules-and-examples.md](docs/llm-rules-and-examples.md) | LLM 输出硬约束、前置步骤依赖、5 种常见场景模板 |
| [minimal-example.md](docs/minimal-example.md) | 最小可用示例（建表 → 建索引 → 加载 → 插入 → 搜索） |
