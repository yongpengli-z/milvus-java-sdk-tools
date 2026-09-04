# milvus-sdk-java 3.0.4 新增接口

本项目从 3.0.3 升级到 3.0.4 后，对两个版本的发布 JAR 的公开类和 `MilvusClientV2` 方法进行了比对。

## 结论

3.0.4 没有新增独立的 `MilvusClientV2` RPC 方法，也没有移除已有 RPC。新增能力附加在已有的 `MilvusClientV2.search(SearchReq)` 调用上：

| 新增公开接口 | 作用 | 项目组件 |
|---|---|---|
| `SearchReq.orderByFields(List<OrderByField>)` | 对向量检索结果按标量字段升序或降序排序 | `SearchOrderByComp` / `SearchOrderByParams` |
| `SearchReq.searchAggregation(SearchAggregation)` | 在向量检索结果上进行分桶聚合 | `SearchAggregationComp` / `SearchAggregationParams` |
| `SearchResp.getAggregationBuckets()` | 读取每个 query 对应的聚合 buckets | `SearchAggregationResult.aggregationBuckets` |

新增的请求类型位于 `io.milvus.v2.service.vector.request.aggregation`：

- `OrderByField`：常规 search 的标量排序字段。
- `SearchAggregation`：分组字段、bucket 数量、指标、排序、top hits 和嵌套聚合。
- `MetricSpec` / `MetricOps`：`AVG`、`SUM`、`COUNT`、`MIN`、`MAX` 指标。
- `OrderSpec`：bucket 排序（指标别名、`_count` 或 `_key`）。
- `TopHitsSpec` / `SortSpec`：每个 bucket 内返回和排序命中记录。
- `AggDirection`：`ASC` / `DESC`。

新增的响应类型位于 `io.milvus.v2.service.vector.response.aggregation`：

- `AggregationBucket`：group key、count、指标、top hits 与子 buckets。
- `AggregationHit`：bucket 内命中的 ID、score 和字段。

## 使用限制

这些是 SDK 客户端接口；目标 Milvus 服务端也必须支持对应请求。服务端不支持时，组件保留服务端错误并以 `EXCEPTION` 返回。具体参数和 JSON 示例见：

- [排序检索组件](../components/SearchOrderByParams.md)
- [聚合检索组件](../components/SearchAggregationParams.md)
