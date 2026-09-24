# UpsertParams

更新/插入数据。对应组件：`custom.components.UpsertComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/空 |
| `collectionNamePrefix` | String | 否 | `""` | 非空时进入**多 collection 模式**：对池子中前缀命中的**每个** collection 各 upsert `numEntries` 条 |
| `collectionRangeStart` | int | 否 | `-1` | 区间起始（`>=0` 启用多 collection 模式）。数字后缀按后缀数值；否则按名称排序后下标切片 |
| `collectionRangeEnd` | int | 否 | `-1` | 区间结束（开区间，`<=0` 表示到末尾） |
| `partitionName` | String | 否 | `""` | |
| `startId` | long | 是 | `0` | |
| `numEntries` | long | 是 | `1500000` | |
| `batchSize` | long | 是 | `1000` | |
| `numConcurrency` | int | 是 | `1` | |
| `targetQps` | int | 否 | `0` | |
| `runningMinutes` | long | 否 | `0` | |
| `fieldDataSourceList` | List | 否 | `[]` | 字段级数据源，与 InsertParams 用法相同 |
| `generalDataRoleList` | List | 否 | `[]` | 不使用建议传 `[]` |
| `retryAfterDeny` | boolean | 否 | `false` | |
| `lengthFactor` | double | 否 | `0` | 与 InsertParams 语义一致 |
| `nullableRatio` | double | 否 | `0.5` | |
| `partialUpdate` | boolean | 否 | `false` | 是否启用部分更新 |
| `updateFieldNames` | List | 否 | `[]` | 部分更新的字段名列表（仅 `partialUpdate=true` 时生效） |
| `pkFromFilter` | String | 否 | `""` | 非空时：upsert 前先按该 filter 查询现有 PK（上限 min(numEntries,16384)），用真实 PK 作为 upsert 主键（不足循环复用）。用于"upsert 已有行"场景（如 autoID PK 保留验证） |
| `verifyPkPreserved` | boolean | 否 | `false` | 仅 `pkFromFilter` 非空时生效：upsert 后用同一 filter 重查 PK 集合与发送集合双向比对，结果写入 assertMessages |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## 多 collection 模式

设置 `collectionNamePrefix`（非空）或 `collectionRangeStart`（`>=0`）即进入多 collection 模式（语义与 InsertParams 完全一致）：

- 目标集合 = 对 `globalCollectionNames` 池子先按 `collectionNamePrefix` 过滤，再按 `[collectionRangeStart, collectionRangeEnd)` 切分（复用 `CommonFunction.filterCollectionPool`）。
- 过滤规则：前缀命中名称为「前缀+纯数字后缀」时按后缀数值过滤（前导零不影响）；否则按名称排序后取下标切片。`collectionRangeEnd<=0` 表示到末尾。
- **对命中的每个 collection 各 upsert `numEntries` 条**（不是总量平均分配）。
- 此模式下 `collectionRule`/`collectionName` 被忽略。
- `numConcurrency` 语义变为**并发 collection 数**（每个 collection 内部按 `batchSize` 串行）。
- `pkFromFilter`/`verifyPkPreserved`/`partialUpdate` 等仍按**每个 collection** 独立生效。
- 返回结果为聚合值：`totalCount`/`successCount`/`failCount`，`numEntries` 为所有 collection 写入总量。
- 数据集信息（`fieldDataSourceList` 对应的数据集目录遍历与文件行数统计）在整个 Upsert 步骤只预加载一次，所有 collection 共用同一套数据集，不会逐 collection 重复检查。

```json
{
  "UpsertParams_0": {
    "collectionNamePrefix": "new_col_15k_",
    "collectionRangeStart": 0, "collectionRangeEnd": 10000,
    "numEntries": 15000, "batchSize": 1000, "numConcurrency": 10,
    "fieldDataSourceList": [], "generalDataRoleList": []
  }
}
```

## targetEndpoint

用于 Global Cluster 场景选择 Upsert 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

## 注意事项

- **没有顶层 `dataset` 字段**，数据集只能通过 `fieldDataSourceList` 指定。
- **autoID 场景**：即使主键 `autoId: true`，Upsert 数据中**也必须包含主键值**。框架自动生成主键数据。
- **部分更新**：`partialUpdate: true` 时，仅更新 `updateFieldNames` 中指定的字段，其余保持不变。`updateFieldNames` 不需要包含主键字段。
- **性能测试建议**：添加多个组件，设置不同 `numConcurrency` 递增压力。

## JSON 示例

```json
{
  "UpsertParams_0": {
    "numEntries": 10000, "batchSize": 1000, "numConcurrency": 1,
    "fieldDataSourceList": [], "generalDataRoleList": [],
    "targetEndpoint": ""
  }
}
```

**部分更新示例**：

```json
{
  "UpsertParams_0": {
    "numEntries": 10000, "batchSize": 1000, "numConcurrency": 1,
    "partialUpdate": true,
    "updateFieldNames": [{"fieldName": "varchar_col"}, {"fieldName": "int_col"}],
    "fieldDataSourceList": [], "generalDataRoleList": [],
    "targetEndpoint": ""
  }
}
```
