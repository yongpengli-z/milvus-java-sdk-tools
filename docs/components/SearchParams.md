# SearchParams

向量搜索。对应组件：`custom.components.SearchComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | |
| `collectionRule` | String | 是 | `""` | `random`/`sequence`/`sequence_per_request`/空 |
| `collectionNamePrefix` | String | 否 | `""` | collection 名前缀过滤（见下文「Collection 池过滤与分割」） |
| `collectionRangeStart` | int | 否 | `-1` | 池区间起始，>=0 启用区间模式（见下文） |
| `collectionRangeEnd` | int | 否 | `-1` | 池区间结束（开区间），<=0 表示到末尾 |
| `queryDataset` | String | 否 | `""` | query 数据集名称（见下文「Query 数据集」），不从底库捞查询输入 |
| `queryVectors` | List<List<Float>> | 否 | `null` | 显式 FloatVector query corpus，与 `queryDataset` 互斥；至少包含 `nq` 条同维有限值向量 |
| `captureSearchResults` | boolean | 否 | `false` | 仅用于单次正确性预检；要求 `numConcurrency=1`、`runningCount=1`，结果包含完整 `searchResults` |
| `annsField` | String | **是** | | 向量字段名。**强烈建议显式指定** |
| `nq` | int | 是 | `1` | query vectors 数量 |
| `topK` | int | 是 | `1` | |
| `outputs` | List | 建议必填 | `[]` | 输出字段 |
| `filter` | String | 否 | `""` | Milvus expr（支持 `$fieldName` 占位符） |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | 按时间循环 |
| `runningCount` | long | 否 | `0` | 按次数循环：>0 时每线程跑满 N 次后停止（次数优先，不再看时间） |
| `randomVector` | boolean | 是 | `true` | |
| `searchLevel` | int | 否 | `1` | dense 搜索参数 `{"level": N}`；**sparse（含 BM25 function 输出字段）忽略**，sparse 请求不注入 level |
| `indexAlgo` | String | 否 | `""` | |
| `targetQps` | double | 否 | `0` | |
| `generalFilterRoleList` | List | 否 | `[]` | filter 占位符替换规则。不使用传 `[]` |
| `partitionNames` | List | 否 | `[]` | |
| `ignoreError` | boolean | 否 | `false` | |
| `timeout` | long | 否 | `800` | SDK 请求超时（ms），0=默认 800ms |
| `groupByField` | String | 否 | `""` | group-by 分组字段名，非空时按该标量字段分组返回 |
| `groupByFields` | List<String> | 否 | `null` | 通过 `group_by_fields` 传一列或多列，与 `groupByField` 互斥；JSON path 只能单列 |
| `groupByJsonType` | String | 否 | `null` | 单列 JSON path 分组的类型转换：`Bool`/`Int8`/`Int16`/`Int32`/`Int64`/`VarChar`；数值叶子建议显式设置 |
| `groupByStrictCast` | Boolean | 否 | `null` | 单列 JSON path 分组时传 `strict_cast`；未设置则使用服务端默认 |
| `groupSize` | int | 否 | `0` | 每组返回条数（group_size），仅 groupByField 非空时生效，0=服务端默认 1 |
| `strictGroupSize` | boolean | 否 | `false` | 严格组大小（strict_group_size），true=每组严格返回 groupSize 条（不足则少返回） |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## Collection 池过滤与分割

Search 的目标 collection 从进程内全局池（Initial/Create/Restore 组件维护）中选择：

- 显式指定 `collectionName` 且 `collectionRule` 为空时直接使用该名称，**不依赖池子**（池子只由 Initial/Create/Restore 填充，Initial 仅列 default db；对 backup 恢复/非 default db 的既有 collection，池子为空也能搜）。只有用池子选择能力（`collectionRule`/`collectionNamePrefix`/区间）且池子为空时才报错
- `collectionRule`：`""`=显式 `collectionName` 或池子最后一个；`random`=池内随机；`sequence`=按步骤轮询（每步骤选一个，整个步骤固定）；`sequence_per_request`=**每个请求**轮换取下一个（全局原子游标，跨线程唯一；总请求数 ≤ 池子大小时每个 collection 恰好被搜一次，适合测多 collection 并发上限 QPS）
- `collectionNamePrefix`：非空时先按前缀过滤池子再做选择；匹配不到直接报错
- `collectionRangeStart`/`collectionRangeEnd`：>=0 启用区间模式，取 `[start,end)`（开区间，`end`<=0 表示到末尾）。两种模式：
  - **数字后缀模式**（前缀非空且命中名称为 `前缀+纯数字后缀`，如 `multi_tenant_1000_0000001`）：按后缀**数值**过滤，`start`/`end` 直接对应名字里的数字，前导零不影响——填 `1` 即匹配 `..._0000001`，`[1,500001)` 命中 `multi_tenant_1000_0000001 ~ multi_tenant_1000_0500000`。即使池子里有缺号也不偏移
  - **位置切片模式**（无前缀或后缀非纯数字）：前缀过滤后**按名称排序**再取下标切片，用于多 client 物理分割（如 client0 取 `[0,334)`、client1 取 `[334,668)`），不依赖命名规律

## Query 数据集

`queryDataset` 指定后，查询输入（向量/文本）从数据集文件**全量加载**，不再从 collection 底库捞取；为空保持原有逻辑。对应 `custom.common.QueryDatasetEnum`：

| datasetName | 类型 | 数据文件 |
|-------------|------|----------|
| `widetable` | vector（FloatVec，768d） | `/test/milvus/raw_data/widetable/emb_768.npy`（10000 条） |
| `widetable_bm25` | text（EmbeddedText，BM25 查询文本） | `/test/milvus/raw_data/widetable/bm25_title_short.txt`（2000 条） |

填错名称会 log.warn 告警并回退为从底库捞取。

`queryVectors` 是可在 Search 与 SearchAggregation 配置中复用的显式 FloatVector 池；`randomVector=false` 时各 worker 按请求次数循环取 `nq` 条（与 Aggregation 的性能模式一致），`true` 时每次从池中随机取样。

## 按次数运行

`runningCount` > 0 时进入次数模式：每个线程跑满 N 次后停止（次数优先，不再看 `runningMinutes`）。失败请求也计入次数。
**只跑一次**：`numConcurrency=1` 且 `runningCount=1`，单样本也能正常输出 avg/TPxx/passRate。
配合 `sequence_per_request` 遍历 N 个 collection 各搜一次：`numConcurrency=1`，`runningCount=N`，整体 avg/TP99 原生输出。

## targetEndpoint

用于 Global Cluster 场景选择 Search 访问的 endpoint：

- `""` / `primary`：使用 primary/default client
- `global`：使用 GDN 统一入口
- `secondary`：使用第一个 secondary
- `secondary_0` / `secondary_1`：使用指定下标的 secondary
- `https://...` / `http://...`：直接连接指定 URI

## Array of Struct 搜索

搜索 Struct 中的向量字段时，`annsField` 格式为 `<structFieldName>[<subFieldName>]`：
- ✅ `clips[clip_embedding]`
- ❌ `clips.clip_embedding`

该向量字段必须已建索引。

## 注意事项

- **性能测试建议**：添加多个 SearchParams 组件，设置不同 `numConcurrency`（1/5/10/20/50）递增压力。
- **group-by 搜索**：`groupByField` 保留单列旧请求；`groupByFields` 经 search params 传新协议（允许单列或多列），其中 JSON path 写为 `meta["g100"]` 且只可单列；数值 JSON 叶子可配 `groupByJsonType="Int64"`，不配时服务端默认按字符串取值；多列与 JSON path 组合会报错。strict 优化路径（2.6.23+，PR#53306）生效条件：`strictGroupSize=true` + `groupSize>1` + `nq=1`。
- **group-by 下 passRate 口径**：新 `groupByFields` 路径按 RPC 成功率统计，不对返回条数作语义断言；旧 `groupByField` 路径保持原有 hit 数口径，strict 模式期望 `topK*groupSize` 条，非 strict 模式期望 `topK` 条。
- **结果预检**：`captureSearchResults=true` 仅允许单 worker、单请求，并将 SDK 返回的命中实体/分数写入步骤结果；测案需在 `outputs` 中指定待检查字段，并自行验证分组语义，性能步骤保持默认 `false`。
- **sparse 向量搜索的 passRate 口径**：`annsField` 为 SparseFloatVector（含 BM25 function 输出字段）时自动识别，pass = 请求无异常即成功，不要求返回满 topK（BM25/稀疏搜索返回不满 topK 属正常）；dense 向量仍按"返回条数 == 期望数"判定。最终日志会额外打印 sparse 模式的 avg hit count。
- **RPC 统计**：`rpcSuccessNum`/`rpcFailureNum`/`requestRps`（所有尝试）/`rpcSuccessRps`（成功 RPC）与旧路径基于 hit 数的 `passRate`/`rps` 分开；新 `groupByFields` 的 `passRate`/`rps` 仅表示 RPC 成功，不表示结果正确。

## JSON 示例

```json
{
  "SearchParams_0": {
    "annsField": "vec", "nq": 1, "topK": 10, "outputs": ["*"],
    "numConcurrency": 10, "runningMinutes": 1, "runningCount": 0,
    "collectionRule": "", "collectionNamePrefix": "",
    "collectionRangeStart": -1, "collectionRangeEnd": -1,
    "queryDataset": "", "randomVector": true,
    "generalFilterRoleList": [], "partitionNames": [],
    "targetEndpoint": ""
  }
}
```

strict group-by（每组 3 条，共 50 条，输出分组字段）：

```json
{
  "SearchParams_0": {
    "annsField": "vec", "nq": 1, "topK": 50, "outputs": ["grp"],
    "groupByField": "grp", "groupSize": 3, "strictGroupSize": true,
    "numConcurrency": 1, "runningMinutes": 1, "runningCount": 100,
    "collectionRule": "", "collectionNamePrefix": "",
    "collectionRangeStart": -1, "collectionRangeEnd": -1,
    "queryDataset": "", "randomVector": true,
    "generalFilterRoleList": [], "partitionNames": [],
    "targetEndpoint": ""
  }
}
```

遍历 1000 个 collection 各搜一次（单 client 串行，原生输出整体 avg/TP99）：

```json
{
  "SearchParams_0": {
    "annsField": "vec", "nq": 1, "topK": 10,
    "collectionRule": "sequence_per_request", "collectionNamePrefix": "wt_",
    "numConcurrency": 1, "runningCount": 1000, "runningMinutes": 1,
    "randomVector": true, "generalFilterRoleList": [], "partitionNames": []
  }
}
```
