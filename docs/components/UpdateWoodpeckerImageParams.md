# UpdateWoodpeckerImageParams

通过 Cloud Ops 提交 Woodpecker image upgrade workflow。对应组件：`custom.components.UpdateWoodpeckerImageComp`

调用 Cloud Ops：

- `POST /api/v1/ops/resource/woodpecker/cluster/upgrade`

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `regionId` | String | 否 | 当前环境 `regionId` | Woodpecker 所在 Region |
| `woodpeckerId` | String | 是 | `""` | Woodpecker 集群 ID |
| `newImageTag` | String | 是 | `""` | 目标 Woodpecker image tag |

## 注意事项

- 前端 image 下拉使用 Woodpecker `insType=8` 搜索。
- 如果前端选择值形如 `dbVersion(tag)`，执行时会取括号中的 `tag` 传给 Cloud Ops。
- 组件只提交 upgrade workflow 并返回 `processInstanceId`，不等待 workflow 完成。

## JSON 示例

```json
{"UpdateWoodpeckerImageParams_0": {"regionId": "aws-us-west-2", "woodpeckerId": "wpawsusw-xxxx", "newImageTag": "master-20260803-abcdef"}}
```
