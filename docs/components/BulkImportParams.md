# BulkImportParams

批量导入 `.npy` 格式数据文件。对应组件：`custom.components.BulkImportComp`

> **注意**：该组件当前处于**开发中状态**（代码已注释），暂时不可用。

## 参数

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|------|------|:----:|--------|------|
| `filePaths` | List<List> | 建议必填 | `[]` | 文件路径二维数组，按 batch 组织 |
| `collectionName` | String | 否 | `""` | |
| `partitionName` | String | 否 | `""` | |
| `dataset` | String | 否 | `"random"` | 数据集类型标识 |

## JSON 示例

```json
{
  "BulkImportParams_0": {
    "filePaths": [
      ["data/batch1/vectors.npy", "data/batch1/ids.npy"],
      ["data/batch2/vectors.npy", "data/batch2/ids.npy"]
    ],
    "dataset": "random"
  }
}
```
