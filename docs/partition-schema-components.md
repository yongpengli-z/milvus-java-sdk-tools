#### 5.2.7 分区管理：Create / Drop / List / Has / Load / Release Partition

##### 5.2.7.1 创建分区：`CreatePartitionParams`

对应组件：`custom.components.CreatePartitionComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`partitionName`**（string，必填）：要创建的分区名称。

##### 5.2.7.2 删除分区：`DropPartitionParams`

对应组件：`custom.components.DropPartitionComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`partitionName`**（string，必填）：要删除的分区名称。

##### 5.2.7.3 列出分区：`ListPartitionsParams`

对应组件：`custom.components.ListPartitionsComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。

##### 5.2.7.4 检查分区是否存在：`HasPartitionParams`

对应组件：`custom.components.HasPartitionComp`

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`partitionName`**（string，必填）：要检查的分区名称。

##### 5.2.7.5 加载分区：`LoadPartitionsParams`

对应组件：`custom.components.LoadPartitionsComp`

**用途**：加载指定分区到内存。与 `LoadParams`（加载整个 collection）不同，该组件仅加载指定的分区，适用于只需要查询部分分区数据的场景。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`partitionNames`**（list，必填）：要加载的分区名称列表。示例：`["partition_1", "partition_2"]`。

##### 5.2.7.6 释放分区：`ReleasePartitionsParams`

对应组件：`custom.components.ReleasePartitionsComp`

**用途**：释放指定分区的内存。与 `ReleaseParams`（释放整个 collection）不同，该组件仅释放指定的分区。

字段：

- **`collectionName`**（string，可空）：前端默认 `""`。为空时使用最后一个 collection。
- **`partitionNames`**（list，必填）：要释放的分区名称列表。示例：`["partition_1", "partition_2"]`。

**分区管理完整流程示例**：

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "my_collection",
    "shardNum": 1,
    "numPartitions": 0,
    "enableDynamic": false,
    "fieldParamsList": [
      {
        "dataType": "Int64",
        "fieldName": "id_pk",
        "primaryKey": true,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      },
      {
        "dataType": "FloatVector",
        "fieldName": "vec",
        "dim": 128,
        "primaryKey": false,
        "autoId": false,
        "partitionKey": false,
        "nullable": false,
        "enableMatch": false,
        "enableAnalyzer": false,
        "analyzerParamsList": []
      }
    ]
  },
  "CreateIndexParams_1": {
    "collectionName": "my_collection",
    "indexParams": [
      {"fieldName": "vec", "indextype": "AUTOINDEX", "metricType": "L2"}
    ]
  },
  "CreatePartitionParams_2": {
    "collectionName": "my_collection",
    "partitionName": "partition_a"
  },
  "CreatePartitionParams_3": {
    "collectionName": "my_collection",
    "partitionName": "partition_b"
  },
  "ListPartitionsParams_4": {
    "collectionName": "my_collection"
  },
  "HasPartitionParams_5": {
    "collectionName": "my_collection",
    "partitionName": "partition_a"
  },
  "LoadPartitionsParams_6": {
    "collectionName": "my_collection",
    "partitionNames": ["partition_a"]
  },
  "InsertParams_7": {
    "collectionName": "my_collection",
    "partitionName": "partition_a",
    "numEntries": 10000,
    "batchSize": 1000,
    "numConcurrency": 5,
    "fieldDataSourceList": [],
    "generalDataRoleList": []
  },
  "ReleasePartitionsParams_8": {
    "collectionName": "my_collection",
    "partitionNames": ["partition_a"]
  }
}
```

**关键点**：
- `CreatePartitionParams` 用于创建分区，分区名称必须唯一
- `ListPartitionsParams` 返回指定 collection 的所有分区列表
- `HasPartitionParams` 检查分区是否存在，返回 boolean 结果
- `LoadPartitionsParams` 与 `LoadParams` 的区别：前者加载指定分区，后者加载整个 collection
- `ReleasePartitionsParams` 与 `ReleaseParams` 的区别：前者释放指定分区，后者释放整个 collection
- **注意**：`numPartitions`（CreateCollectionParams 中的字段）是 Partition Key 的分区数量配置，与手动创建分区是两种不同的分区机制。手动创建分区不需要设置 `numPartitions`

---

#### 5.3 Collection 结构变更：AddField / Rename / Describe

##### 5.3.1 AddCollectionField：`AddCollectionFieldParams`

对应组件：`custom.components.AddCollectionFieldComp`

字段较多，常用字段如下：

- **`collectionName`**（string，可空）：前端默认 `""`。
- **`databaseName`**（string，可空）：前端默认 `""`。
- **`fieldName`**（string，可空）：前端默认 `""`。
- **`dataType`**（DataType 枚举字符串）：前端默认 `""`（占位；建议用枚举名或 `null`/不传，不要传空串）。
- **`defaultValue`**（string，可空）：注意组件内部会按 `dataType` 解析成对应类型。前端默认 `""`。
- **`enableDefaultValue`**（boolean，前端必填）：前端默认 `false`。
- **`isNullable`**（Boolean，前端必填）：前端默认 `true`。
- **`isPrimaryKey/isPartitionKey/isClusteringKey`**（Boolean，可空）
- **`autoID`**（Boolean，可空）
- **`dimension/maxLength/maxCapacity/elementType`**（按类型填写）
- **`enableAnalyzer/enableMatch`**（Boolean，前端必填）：前端默认 `false`。
- **`analyzerParamsList`**（list，可空）：前端默认 `[{paramsKey:"", paramsValue:""}]`（占位；不使用建议传 `[]`）。

##### 5.3.2 RenameCollection：`RenameCollectionParams`

- **`collectionName`**（string，可空）
- **`newCollectionName`**（string）
- **`databaseName`**（string，可空）

##### 5.3.3 DescribeCollection：`DescribeCollectionParams`

- **`collectionName`**（string，可空）
- **`databaseName`**（string，可空）

---

