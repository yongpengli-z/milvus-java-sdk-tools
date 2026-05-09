# CreateQueryClusterParams

Creates a VectorLake QueryCluster (`in07`). This component uses cloud-service
`POST /cloud/v1/vectorlake/query-cluster`; it does not use the Milvus
`classId` create path.

## Parameters

| Field | Type | Required | Default | Description |
|------|------|:----:|--------|------|
| `clusterName` | String | Yes | `""` | QueryCluster display name |
| `cuSize` | int | Yes | `8` | QueryCluster CU, must be `>= 8` |
| `projectId` | String | No | default project | Target project ID |
| `projectName` | String | No | `""` | Used only when `projectId` is empty |
| `regionId` | String | No | env region | Target region |
| `sessionTTL` | String | No | `60s` | VectorLake session TTL if a new `in06` is created |
| `maxQueryNodeCU` | Integer | No | backend default | VectorLake capacity setting if a new `in06` is created |
| `maxQueryNodeReplicas` | Integer | No | backend default | VectorLake capacity setting if a new `in06` is created |
| `vectorLakeDbVersion` | String | No | `""` | Expected `in06` version. If set, the component creates `in06` when absent and upgrades it before creating `in07` |
| `queryClusterDbVersion` | String | No | `""` | Optional `in07` QueryNode version to apply after creation |
| `accountEmail` | String | No | default account | Cloud account email |
| `accountPassword` | String | No | default account | Cloud account password |

## Notes

- `in07` uses `cuSize`, not `class-1-enterprise` / `classId`.
- `vectorLakeDbVersion` and `queryClusterDbVersion` are different image
  streams. `vectorLakeDbVersion` is resolved with `ins_type=6`; 
  `queryClusterDbVersion` is resolved with `ins_type=7`.
- cloud-service cannot pin the `in06` image when it implicitly creates VectorLake.
  If `vectorLakeDbVersion` is set, the component creates `in06` when absent,
  waits for RUNNING, upgrades it, then creates `in07`. If `in06` already exists,
  the component upgrades it directly before creating `in07`.
- cloud-service also cannot pin the `in07` image at create time. Use
  `queryClusterDbVersion` to upgrade after create.
- The component always initializes clients after creation by resolving the current
  account's personal API key and using the QueryCluster endpoint returned by
  cloud-service.

## Example

```json
{
  "CreateQueryClusterParams_0": {
    "clusterName": "qc-test",
    "cuSize": 8,
    "regionId": "aws-us-west-2",
    "sessionTTL": "60s",
    "vectorLakeDbVersion": "vectorlake-20260509-xxxxxxx",
    "queryClusterDbVersion": "querycluster-20260509-yyyyyyy",
    "accountEmail": "",
    "accountPassword": ""
  }
}
```
