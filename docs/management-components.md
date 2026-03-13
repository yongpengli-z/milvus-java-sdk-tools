##### 5.1.10 Release：`ReleaseParams`

对应组件：`custom.components.ReleaseCollectionComp`

字段：

- **`releaseAll`**（boolean，前端必填）：前端默认 `false`。
- **`collectionName`**（string，可空）：前端默认 `""`。

##### 5.1.11 Drop Collection：`DropCollectionParams`

对应组件：`custom.components.DropCollectionComp`

字段：

- **`dropAll`**（boolean，前端必填）：前端默认 `false`。
- **`collectionName`**（string，可空）：前端默认 `""`。
- **`databaseName`**（string，可空）：前端默认 `""`。

##### 5.1.12 Drop Index：`DropIndexParams`

对应组件：`custom.components.DropIndexComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`fieldName`**（string，前端必填）：要 drop 的索引字段名。前端默认 `""`。

##### 5.1.13 Describe Index：`DescribeIndexParams`

对应组件：`custom.components.DescribeIndexComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`databaseName`**（string，可空）：前端默认 `""`。
- **`fieldName`**（string，必填）：要查看索引的字段名。
- **`indexName`**（string，可空）：索引名称。前端默认 `""`。

##### 5.1.14 List Indexes：`ListIndexesParams`

对应组件：`custom.components.ListIndexesComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`databaseName`**（string，可空）：前端默认 `""`。

##### 5.1.15 Compact：`CompactParams`

对应组件：`custom.components.CompactComp`

字段：

- **`compactAll`**（boolean，前端必填）：前端默认 `false`。
- **`collectionName`**（string，可空）：前端默认 `""`。
- **`clustering`**（boolean，前端必填）：注意：Java 字段名是 `isClustering`，JSON key 建议用 `clustering` 或 `isClustering`。前端默认 `false`。

##### 5.1.16 List Collections：`ListCollectionsParams`

对应组件：`custom.components.ListCollectionsComp`

**用途**：列出当前 database 下的所有 collection 名称。

字段：

- **`databaseName`**（string，可空）：前端默认 `""`。为空时使用当前连接的 database。

##### 5.1.17 Has Collection：`HasCollectionParams`

对应组件：`custom.components.HasCollectionComp`

**用途**：检查指定 collection 是否存在，返回 boolean 结果。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`databaseName`**（string，可空）：前端默认 `""`。

##### 5.1.18 Get Load State：`GetLoadStateParams`

对应组件：`custom.components.GetLoadStateComp`

**用途**：查询指定 collection 的加载状态（是否已加载到内存），返回 boolean 结果。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`databaseName`**（string，可空）：前端默认 `""`。

##### 5.1.19 Get（按 ID 获取实体）：`GetParams`

对应组件：`custom.components.GetComp`

**用途**：按 ID 获取指定实体，类似于 KV 存储的 get 操作。与 Query 不同，Get 直接通过主键 ID 获取，不需要 filter 表达式。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`databaseName`**（string，可空）：前端默认 `""`。
- **`ids`**（list，必填）：要获取的实体 ID 列表。示例：`[1, 2, 3]` 或 `["id_001", "id_002"]`。
- **`outputFields`**（list，可空）：输出字段列表。前端默认 `[]`。
- **`partitionNames`**（list，可空）：分区名称列表。前端默认 `[]`。

---

