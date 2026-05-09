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
| `autoCreateVectorLake` | boolean | No | `true` | Create `in06` through the QueryCluster endpoint when `vectorLakeDbVersion` is empty and `in06` is absent |
| `queryClusterDbVersion` | String | No | `""` | Optional `in07` QueryNode version to apply after creation |
| `autoUpgradeQueryCluster` | boolean | No | `false` | Upgrade `in07` after creation |
| `forceUpgradeQueryCluster` | boolean | No | `true` | Force live QN upgrade |
| `apiKey` | String | No | `""` | API key token for client initialization |
| `usePersonalApiKey` | boolean | No | `true` | Try to use current account's personal API key if `apiKey` is empty |
| `connectAfterCreate` | boolean | No | `true` | Initialize Milvus clients after creation |
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
  `queryClusterDbVersion` + `autoUpgradeQueryCluster=true` to upgrade after create.
- Existing API keys usually cannot be recovered as plaintext. If the API key list
  returns only metadata, pass `apiKey` explicitly or set `connectAfterCreate=false`.

## Example

```json
{
  "CreateQueryClusterParams_0": {
    "clusterName": "qc-test",
    "cuSize": 8,
    "regionId": "aws-us-west-2",
    "sessionTTL": "60s",
    "vectorLakeDbVersion": "vectorlake-20260509-xxxxxxx",
    "autoCreateVectorLake": true,
    "queryClusterDbVersion": "querycluster-20260509-yyyyyyy",
    "autoUpgradeQueryCluster": true,
    "apiKey": "",
    "connectAfterCreate": true
  }
}
```
