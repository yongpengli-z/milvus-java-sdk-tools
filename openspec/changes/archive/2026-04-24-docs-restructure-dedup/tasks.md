## 1. 改造 README.md

- [x] 1.1 重写 README.md：项目定位 + 启动参数说明（合并 params-overview.md §2-3）
- [x] 1.2 添加组件功能索引表：所有独立组件按类别分组，每行 `组件名 | 功能 | 文件链接`
- [x] 1.3 添加操作依赖顺序说明（合并 llm-rules-and-examples.md §9.1）
- [x] 1.4 添加 JSON 构造规则（合并 params-overview.md §4、llm-prompt.md §1）
- [x] 1.5 添加枚举速查：DataType 列表、IndexType/MetricType 约束矩阵、env 枚举、collectionRule、fieldDataSourceList 数据集（合并 enums-reference.md）
- [x] 1.6 添加 LLM 智能补全规则（合并 llm-rules-and-examples.md §9.2-9.3）
- [x] 1.7 添加 JSON 输出格式要求 + 可省略/不可省略字段清单（合并 general-data-role.md §8）
- [x] 1.8 添加常见场景模板（合并 llm-rules-and-examples.md §9.2 的 5 种场景 + minimal-example.md）

## 2. 创建组件文件 — Collection/Index/Load

- [x] 2.1 创建 docs/components/CreateCollectionParams.md（含 FieldParams、StructFieldParams、FunctionParams、analyzerParamsList、Array of Struct 示例、BM25 示例、enableDynamic/$meta 说明、properties）
- [x] 2.2 创建 docs/components/CreateIndexParams.md（含 IndexParams 子结构、jsonPath/jsonCastType、buildLevel、Array of Struct 索引格式、Cloud AUTOINDEX 约束）
- [x] 2.3 创建 docs/components/LoadParams.md

## 3. 创建组件文件 — 数据操作

- [x] 3.1 创建 docs/components/InsertParams.md（含 fieldDataSourceList 用法、dynamic field 数据生成、Array of Struct 数据生成、lengthFactor、nullableRatio）
- [x] 3.2 创建 docs/components/RestfulInsertParams.md
- [x] 3.3 创建 docs/components/SearchParams.md（含 Array of Struct 搜索 annsField 格式）
- [x] 3.4 创建 docs/components/QueryParams.md（含 NPE 必填字段警告、filter/ids 约束、最小可运行示例）
- [x] 3.5 创建 docs/components/UpsertParams.md（含 partialUpdate、autoID 说明、lengthFactor）
- [x] 3.6 创建 docs/components/DeleteParams.md
- [x] 3.7 创建 docs/components/FlushParams.md
- [x] 3.8 创建 docs/components/GetParams.md

## 4. 创建组件文件 — 高级搜索/迭代

- [x] 4.1 创建 docs/components/SearchIteratorParams.md
- [x] 4.2 创建 docs/components/QueryIteratorParams.md
- [x] 4.3 创建 docs/components/HybridSearchParams.md（含 searchRequests、ranker/rankerParams 示例）
- [x] 4.4 创建 docs/components/RestfulHybridSearchParams.md
- [x] 4.5 创建 docs/components/RestfulSearchParams.md
- [x] 4.6 创建 docs/components/RecallParams.md
- [x] 4.7 创建 docs/components/QuerySegmentInfoParams.md
- [x] 4.8 创建 docs/components/PersistentSegmentInfoParams.md
- [x] 4.9 创建 docs/components/BulkImportParams.md

## 5. 创建组件文件 — 管理操作

- [x] 5.1 创建 docs/components/ReleaseParams.md
- [x] 5.2 创建 docs/components/DropCollectionParams.md
- [x] 5.3 创建 docs/components/DropIndexParams.md
- [x] 5.4 创建 docs/components/DescribeIndexParams.md
- [x] 5.5 创建 docs/components/ListIndexesParams.md
- [x] 5.6 创建 docs/components/CompactParams.md
- [x] 5.7 创建 docs/components/ListCollectionsParams.md
- [x] 5.8 创建 docs/components/HasCollectionParams.md
- [x] 5.9 创建 docs/components/GetLoadStateParams.md
- [x] 5.10 创建 docs/components/DescribeCollectionParams.md
- [x] 5.11 创建 docs/components/AddCollectionFieldParams.md
- [x] 5.12 创建 docs/components/RenameCollectionParams.md

## 6. 创建组件文件 — 分区管理

- [x] 6.1 创建 docs/components/CreatePartitionParams.md
- [x] 6.2 创建 docs/components/DropPartitionParams.md
- [x] 6.3 创建 docs/components/ListPartitionsParams.md
- [x] 6.4 创建 docs/components/HasPartitionParams.md
- [x] 6.5 创建 docs/components/LoadPartitionsParams.md
- [x] 6.6 创建 docs/components/ReleasePartitionsParams.md

## 7. 创建组件文件 — Database/Alias

- [x] 7.1 创建 docs/components/CreateDatabaseParams.md
- [x] 7.2 创建 docs/components/UseDatabaseParams.md
- [x] 7.3 创建 docs/components/CreateAliasParams.md
- [x] 7.4 创建 docs/components/AlterAliasParams.md
- [x] 7.5 创建 docs/components/DropAliasParams.md
- [x] 7.6 创建 docs/components/ListAliasesParams.md
- [x] 7.7 创建 docs/components/DescribeAliasParams.md

## 8. 创建组件文件 — 流程控制

- [x] 8.1 创建 docs/components/WaitParams.md
- [x] 8.2 创建 docs/components/LoopParams.md（含批量创建 collection 示例、collectionRule 配合用法）
- [x] 8.3 创建 docs/components/ConcurrentParams.md
- [x] 8.4 创建 docs/components/DebugTestParams.md

## 9. 创建组件文件 — Cloud 实例管理

- [x] 9.1 创建 docs/components/CreateInstanceParams.md（含 cuType、dbVersion、token 格式、accountEmail）
- [x] 9.2 创建 docs/components/DeleteInstanceParams.md
- [x] 9.3 创建 docs/components/StopInstanceParams.md
- [x] 9.4 创建 docs/components/ResumeInstanceParams.md
- [x] 9.5 创建 docs/components/RestartInstanceParams.md
- [x] 9.6 创建 docs/components/RollingUpgradeParams.md
- [x] 9.7 创建 docs/components/ModifyParams.md
- [x] 9.8 创建 docs/components/ScaleInstanceParams.md（含 classId 与 replica 编码规则）
- [x] 9.9 创建 docs/components/UpdateIndexPoolParams.md
- [x] 9.10 创建 docs/components/AlterInstanceIndexClusterParams.md
- [x] 9.11 创建 docs/components/UpdateInstanceComponentParams.md
- [x] 9.12 创建 docs/components/RestoreBackupParams.md
- [x] 9.13 创建 docs/components/CreateSecondaryParams.md

## 10. 创建组件文件 — Helm 部署

- [x] 10.1 创建 docs/components/HelmCreateInstanceParams.md（含 milvusMode、依赖组件配置、Woodpecker、资源配置）
- [x] 10.2 创建 docs/components/HelmDeleteInstanceParams.md

## 11. 删除旧文档文件

- [x] 11.1 删除 docs/llm-prompt.md
- [x] 11.2 删除 docs/params-overview.md
- [x] 11.3 删除 docs/minimal-example.md
- [x] 11.4 删除 docs/llm-rules-and-examples.md
- [x] 11.5 删除 docs/enums-reference.md
- [x] 11.6 删除 docs/general-data-role.md
- [x] 11.7 删除 docs/collection-components.md
- [x] 11.8 删除 docs/index-load-components.md
- [x] 11.9 删除 docs/data-components.md
- [x] 11.10 删除 docs/iterator-hybridsearch.md
- [x] 11.11 删除 docs/management-components.md
- [x] 11.12 删除 docs/partition-schema-components.md
- [x] 11.13 删除 docs/database-alias-workflow.md
- [x] 11.14 删除 docs/cloud-components.md
- [x] 11.15 删除 docs/helm-components.md
- [x] 11.16 删除 docs/azure-deploy.md

## 12. 验证

- [x] 12.1 对照旧文档逐一检查，确保所有组件参数、约束、注意事项都已保留到新文档中
- [x] 12.2 检查 README 与组件文件间无重复内容
- [x] 12.3 检查 README 中组件索引表的所有文件链接有效
- [x] 12.4 检查每个组件文件都有至少一个 JSON 示例
