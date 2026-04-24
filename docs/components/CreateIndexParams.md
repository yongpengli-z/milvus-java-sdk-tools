# CreateIndexParams

为 Collection 的字段创建索引。对应组件：`custom.components.CreateIndexComp`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `collectionName` | String | 否 | `""` | 默认使用最后一个 collection |
| `databaseName` | String | 否 | `""` | |
| `indexParams` | List | 建议必填 | `[]` | 索引配置列表。传 `[]` 时系统自动为所有向量字段建索引 |

## IndexParams 子结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `fieldName` | String | 要建索引的字段名 |
| `indextype` | enum | 索引类型（**注意 JSON key 是 `indextype`，不是 `indexType`**） |
| `metricType` | enum | 距离度量类型（仅向量字段需要） |
| `jsonPath` | String | JSON 字段索引路径，如 `field["key1"]["key2"]` |
| `jsonCastType` | String | JSON 字段目标类型：`varchar`/`int64`/`double`/`bool` |
| `buildLevel` | String | HNSW build level：`"0"`=Memory、`"1"`=Balanced（默认）、`"2"`=Performance |

## 向量类型索引约束

| 向量类型 | 推荐索引 | 支持的 MetricType | buildLevel |
|---------|---------|-----------------|------------|
| FloatVector/Float16Vector/BFloat16Vector/Int8Vector | AUTOINDEX 或 HNSW | L2（默认）、COSINE、IP | 仅 Float/Float16/BFloat16 且**仅云上环境** |
| BinaryVector | AUTOINDEX 或 BIN_IVF_FLAT | **HAMMING 或 JACCARD**（不能用 L2/IP/COSINE） | 不支持 |
| SparseFloatVector | AUTOINDEX 或 SPARSE_WAND | BM25 function 输出 → **必须 `BM25`**；否则 → `IP` | 不支持 |
| Array of Struct 向量 | HNSW | **MAX_SIM_L2 / MAX_SIM_IP / MAX_SIM_COSINE** | 不支持 |

## 标量字段索引

- VarChar/Int*/Float/Double/Bool：`STL_SORT` 或 `AUTOINDEX`，**不需要 MetricType**
- JSON / Dynamic Field：`STL_SORT` 或 `AUTOINDEX`，**必须同时设置 `jsonPath` + `jsonCastType`**

## Cloud AUTOINDEX 约束

> **通过 `CreateInstanceParams` 创建的 Zilliz Cloud 托管实例，所有字段索引（含标量）必须使用 `AUTOINDEX`**。提交 HNSW/BITMAP/INVERTED 等 explicit 类型会被拒绝。
>
> 只有 **Helm 部署** 和 **本地环境（devops/fouram）** 才允许 explicit 索引类型。

## Array of Struct 索引格式

字段名格式：`<structFieldName>[<subFieldName>]`（方括号，不是点号）

- ✅ `clips[clip_embedding]`
- ❌ `clips.clip_embedding`

## 注意事项

- `indexParams: []` → 系统自动为所有向量字段建索引
- Array of Struct 中的向量字段**必须建索引**，否则无法搜索
- `buildLevel` 仅在云上环境（awswest/gcpwest/azurewest/alihz/tcnj/hwc）支持

## JSON 示例

**基础索引**：

```json
{
  "CreateIndexParams_0": {
    "collectionName": "",
    "indexParams": [
      {"fieldName": "vec", "indextype": "AUTOINDEX", "metricType": "L2"}
    ]
  }
}
```

**JSON 字段索引**：

```json
{
  "CreateIndexParams_0": {
    "indexParams": [
      {"fieldName": "vec", "indextype": "AUTOINDEX", "metricType": "L2"},
      {"fieldName": "meta_json", "indextype": "AUTOINDEX", "jsonPath": "meta_json[\"category\"]", "jsonCastType": "varchar"}
    ]
  }
}
```

**Array of Struct 向量索引**：

```json
{
  "CreateIndexParams_0": {
    "indexParams": [
      {"fieldName": "clips[clip_embedding]", "indextype": "HNSW", "metricType": "MAX_SIM_L2"},
      {"fieldName": "clips[description_embedding]", "indextype": "HNSW", "metricType": "MAX_SIM_IP"}
    ]
  }
}
```

**BM25 稀疏向量索引**：

```json
{
  "CreateIndexParams_0": {
    "indexParams": [
      {"fieldName": "sparse_embedding", "indextype": "AUTOINDEX", "metricType": "BM25"}
    ]
  }
}
```
