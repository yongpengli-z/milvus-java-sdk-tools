### 10. 一个“最小可用”示例（建议让 LLM 从它改）

> 注意：仓库内 `src/main/resources/example/base.json` 等示例文件会随代码演进调整；如果你要喂给 LLM，请优先以本文给出的字段名/必填字段/枚举取值为准。

```json
{
  "CreateCollectionParams_0": {
    "collectionName": "",
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
        "dataType": "VarChar",
        "fieldName": "varchar_1",
        "maxLength": 256,
        "primaryKey": false,
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
    "collectionName": "",
    "databaseName": "",
    "indexParams": [
      {"fieldName": "vec", "indextype": "AUTOINDEX", "metricType": "L2"}
    ]
  },
  "LoadParams_2": {
    "loadAll": false,
    "collectionName": "",
    "loadFields": [],
    "skipLoadDynamicField": false
  },
  "InsertParams_3": {
    "collectionName": "",
    "collectionRule": "",
    "partitionName": "",
    "startId": 0,
    "numEntries": 10000,
    "batchSize": 1000,
    "numConcurrency": 5,
    "fieldDataSourceList": [],
    "runningMinutes": 0,
    "retryAfterDeny": false,
    "ignoreError": false,
    "generalDataRoleList": []
  },
  "FlushParams_4": {
    "flushAll": false,
    "collectionName": ""
  },
  "SearchParams_5": {
    "collectionName": "",
    "collectionRule": "",
    "annsField": "vec",
    "nq": 1,
    "topK": 5,
    "outputs": ["*"],
    "filter": "",
    "numConcurrency": 10,
    "runningMinutes": 1,
    "randomVector": true,
    "searchLevel": 1,
    "indexAlgo": "",
    "targetQps": 0,
    "generalFilterRoleList": [],
    "ignoreError": true,
    "timeout": 800
  },
  "QueryParams_6": {
    "collectionName": "",
    "collectionRule": "",
    "outputs": ["*"],
    "filter": "id_pk > 10",
    "ids": [],
    "partitionNames": [],
    "limit": 5,
    "offset": 0,
    "numConcurrency": 5,
    "runningMinutes": 1,
    "targetQps": 0,
    "generalFilterRoleList": []
  },
  "ReleaseParams_7": {
    "releaseAll": false,
    "collectionName": ""
  }
}
```


