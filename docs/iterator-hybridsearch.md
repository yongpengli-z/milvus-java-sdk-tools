#### 5.2 进阶：SearchIterator / QueryIterator / Recall / SegmentInfo

##### 5.2.1 SearchIterator：`SearchIteratorParams`

对应组件：`custom.components.SearchIteratorComp`

字段：

- **`collectionName`**（string，可空）
- **`annsFields`**（string，建议必填）：用于从库里 query 出向量样本
- **`vectorFieldName`**（string，建议必填）：传给 SearchIterator 的向量字段名（通常与 `annsFields` 相同）
- **`nq`**（int）
- **`topK`**（int）
- **`batchSize`**（int）：iterator 每次拉取的 batch
- **`outputs`**（list，建议必填）
- **`filter`**（string，可空；作为 `expr`）
- **`metricType`**（string）：只识别 `IP` / `COSINE` / 其它默认 `L2`
- **`params`**（string）：例如 `"{\"level\": 1}"`
- **`numConcurrency`**（int）
- **`runningMinutes`**（long，建议必填且 >0）
- **`randomVector`**（boolean）
- **`indexAlgo`**（string，可空）：索引算法。前端默认 `""`。
- **`useV1`**（boolean）：当前实现未使用该字段（保留）

##### 5.2.2 QueryIterator：`QueryIteratorParams`

对应组件：`custom.components.QueryIteratorComp`

**用途**：迭代式查询，用于大结果集分页拉取。与 `QueryParams` 不同，QueryIterator 会自动分批拉取所有满足条件的结果，适用于需要遍历大量数据的场景。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`filter`**（string，可空）：过滤表达式。前端默认 `""`。
- **`outputFields`**（list，可空）：输出字段列表。前端默认 `[]`。
- **`batchSize`**（int）：iterator 每次拉取的 batch size。前端默认 `100`。
- **`limit`**（long，可空）：最大返回总数，0 表示不限制。前端默认 `0`。
- **`numConcurrency`**（int）：并发线程数。前端默认 `1`。
- **`runningMinutes`**（long）：运行时长（分钟）。前端默认 `1`。
- **`partitionNames`**（list，可空）：分区名称列表。前端默认 `[]`。

##### 5.2.3 HybridSearch：`HybridSearchParams`

对应组件：`custom.components.HybridSearchComp`

**用途**：HybridSearch（混合搜索）支持在同一个 collection 中对多个向量字段进行搜索，并使用融合策略（RRF 或 WeightedRanker）合并结果。这是 Milvus 2.4+ 版本引入的功能。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`collectionRule`**（string，前端必填但可为空）：`random/sequence`。前端默认 `""`（None）。
- **`searchRequests`**（list，前端必填）：混合搜索请求列表，每个元素代表一个向量字段的搜索请求。
  - **`annsField`**（string）：向量字段名
  - **`topK`**（int）：该字段的 topK
  - **`searchParams`**（object，可空）：搜索参数 Map，例如 `{"level": 1}`
  - **`filter`**（string，可空）：该字段的 Milvus expr 过滤表达式（可包含 `$fieldName` 占位符）。每个搜索请求可以有自己的 filter。前端默认 `""`（表示不过滤）
  - **注意**：`metricType` 字段已不再使用，`AnnSearchReq` 构建时不会设置 MetricType，Milvus 会根据索引配置自动使用对应的 MetricType。
- **`ranker`**（string，前端必填）：融合策略类型，可选值：
  - `"RRF"`：Reciprocal Rank Fusion（倒数排名融合），默认值
  - `"WeightedRanker"`：加权排序
- **`rankerParams`**（object，可空）：融合策略参数 Map
  - RRF：`{"k": 60}`（k 为常数，默认 60）
  - WeightedRanker：`{"weights": [0.5, 0.5]}`（权重列表，长度需与 searchRequests 数量一致）
- **`topK`**（int，前端必填）：最终返回的候选数量。前端默认 `10`。
- **`nq`**（int，前端必填）：query 向量数量。前端默认 `1`。
- **`randomVector`**（boolean，前端必填）：是否每次请求随机选择 query 向量。前端默认 `true`。
- **`outputs`**（list，建议必填）：输出字段。前端默认 `[]`。
- **`numConcurrency`**（int，前端必填）：并发线程数。前端默认 `10`。
- **`runningMinutes`**（long，前端必填）：运行时长（分钟）。前端默认 `10`。
- **`targetQps`**（double，可空）：目标 QPS。前端默认 `0`（0=不限制）。
- **`generalFilterRoleList`**（list，可空）：filter 占位符替换规则列表。前端默认是“带 1 条空规则”的占位数组；不使用建议传 `[]`。
- **`ignoreError`**（boolean，可空）：是否忽略错误继续搜索。前端默认 `false`。

**使用场景**：
- 多模态搜索：例如使用 ResNet 和 CLIP 分别提取图片和文本特征，存储为不同向量列，然后进行混合搜索
- 多向量列融合：当同一个 collection 中有多个向量字段时，可以使用 HybridSearch 进行融合搜索

**示例 JSON**：
```json
{
  "HybridSearchParams_0": {
    "collectionName": "",
    "collectionRule": "",
    "searchRequests": [
      {
        "annsField": "image_vector",
        "topK": 10,
        "searchParams": {"level": 1},
        "filter": ""
      },
      {
        "annsField": "text_vector",
        "topK": 10,
        "searchParams": {"level": 1},
        "filter": ""
      }
    ],
    "ranker": "RRF",
    "rankerParams": {"k": 60},
    "topK": 10,
    "nq": 1,
    "randomVector": true,
    "outputs": ["*"],
    "numConcurrency": 10,
    "runningMinutes": 1,
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true
  }
}
```

##### 5.2.3.1 RestfulHybridSearch：`RestfulHybridSearchParams`

对应组件：`custom.components.RestfulHybridSearchComp`

**用途**：功能上与 `HybridSearchParams` 等价（同一 collection 多向量字段 + 融合排序），但通过 Milvus RESTful 接口 `/v2/vectordb/entities/advanced_search` 发起请求，而非 Java SDK。常用于 REST 通道的压测 / 功能验证。

字段（与 `HybridSearchParams` 对齐，仅标注差异点）：

- **`collectionName`** / **`collectionRule`**：与 HybridSearch 一致
- **`searchRequests`**（list，必填）：子请求结构同 HybridSearch
  - **`annsField`**（string）
  - **`topK`**（int）
  - **`searchParams`**（object，例如 `{"level": 1}`）
  - **`filter`**（string）
- **`ranker`** / **`rankerParams`**：同 HybridSearch（`RRF` / `WeightedRanker`）
- **`topK`** / **`nq`** / **`randomVector`** / **`outputs`**：同 HybridSearch
- **`numConcurrency`**：默认 `10`
- **`runningMinutes`**：默认 `10`
- **`targetQps`**（double）：默认 `0`（不限速）
- **`generalFilterRoleList`**：filter 占位符替换规则
- **`ignoreError`**（boolean）：默认 `false`
- **`socketTimeout`**（int，ms，可空）：HTTP Socket 读取超时，默认 `5000`；`0` 表示使用默认值

**示例 JSON**：

```json
{
  "RestfulHybridSearchParams_0": {
    "collectionName": "",
    "collectionRule": "",
    "searchRequests": [
      {
        "annsField": "float_vec",
        "topK": 10,
        "searchParams": {"level": 1},
        "filter": ""
      },
      {
        "annsField": "sparse_vec",
        "topK": 10,
        "searchParams": {},
        "filter": ""
      }
    ],
    "ranker": "RRF",
    "rankerParams": {"k": 60},
    "topK": 10,
    "nq": 1,
    "randomVector": true,
    "outputs": ["*"],
    "numConcurrency": 10,
    "runningMinutes": 1,
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": false,
    "socketTimeout": 5000
  }
}
```

**注意事项**：
- REST 请求体中的向量会按字段类型正确编码：`Float16Vector` / `BFloat16Vector` 会被序列化为 float 数组（而非 base64），Sparse 向量按 `{idx: val}` 结构传输
- `ranker` 值会在发请求前统一转为小写（`RRF` → `rrf`），与 REST API 要求一致
- 走 SDK 的多向量混合搜索请继续使用 `HybridSearchParams`；只有明确需要 REST 通道时才使用本组件

##### 5.2.4 Recall：`RecallParams`

对应组件：`custom.components.RecallComp`

字段：

- **`collectionName`**（string，可空）
- **`annsField`**（string，建议必填）
- **`searchLevel`**（int）

说明：Recall 使用 `CommonFunction.providerSearchVectorDataset` 采样向量并记录 base id，再以 `topK=1` 搜索并比较命中率。

##### 5.2.5 Segment Info（V1）：`QuerySegmentInfoParams` / `PersistentSegmentInfoParams`

对应组件：

- `custom.components.QuerySegmentInfoComp`（`getQuerySegmentInfo`）
- `custom.components.PersistentSegmentInfoComp`（`getPersistentSegmentInfo`）

字段都只有：

- **`collectionName`**（string，可空）

---

##### 5.2.6 BulkImport（批量导入）：`BulkImportParams`

对应组件：`custom.components.BulkImportComp`

**注意**：该组件当前处于**开发中状态**（代码已注释），暂时不可用。

**用途**：批量导入 `.npy` 格式的数据文件到 Milvus。

字段：

- **`filePaths`**（list of list，建议必填）：文件路径二维数组，按 batch/组组织。前端默认：`[]`。
- **`collectionName`**（string，可空）：为空时使用最近创建/记录的 collection。前端默认：`""`。
- **`partitionName`**（string，可空）：前端默认：`""`。
- **`dataset`**（string，可空）：数据集类型标识（由导入逻辑解释）。默认：`"random"`。

**示例 JSON**：

```json
{
  "BulkImportParams_0": {
    "filePaths": [
      ["data/batch1/vectors.npy", "data/batch1/ids.npy"],
      ["data/batch2/vectors.npy", "data/batch2/ids.npy"]
    ],
    "collectionName": "",
    "partitionName": "",
    "dataset": "random"
  }
}
```

---

