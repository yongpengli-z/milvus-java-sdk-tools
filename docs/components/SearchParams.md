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
| `annsField` | String | **是** | | 向量字段名。**强烈建议显式指定** |
| `nq` | int | 是 | `1` | query vectors 数量 |
| `topK` | int | 是 | `1` | |
| `outputs` | List | 建议必填 | `[]` | 输出字段 |
| `filter` | String | 否 | `""` | Milvus expr（支持 `$fieldName` 占位符） |
| `numConcurrency` | int | 是 | `10` | |
| `runningMinutes` | long | 是 | `10` | 按时间循环 |
| `runningCount` | long | 否 | `0` | 按次数循环：>0 时每线程跑满 N 次后停止（次数优先，不再看时间） |
| `randomVector` | boolean | 是 | `true` | |
| `searchLevel` | int | 否 | `1` | |
| `indexAlgo` | String | 否 | `""` | |
| `targetQps` | double | 否 | `0` | |
| `generalFilterRoleList` | List | 否 | `[]` | filter 占位符替换规则。不使用传 `[]` |
| `partitionNames` | List | 否 | `[]` | |
| `ignoreError` | boolean | 否 | `false` | |
| `timeout` | long | 否 | `800` | SDK 请求超时（ms），0=默认 800ms |
| `targetEndpoint` | String | 否 | `""` | Global Cluster 目标入口：`primary`/`global`/`secondary`/`secondary_0`，也可直接传 URI |

## Collection 池过滤与分割

Search 的目标 collection 从进程内全局池（Initial/Create/Restore 组件维护）中选择：

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
