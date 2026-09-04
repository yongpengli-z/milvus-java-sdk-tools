# Drop Collection 百分比删除实现计划

**目标：** 为 DropCollection 组件增加按百分比删除 collection 的能力，并确保成功删除后同步从全局 collection 列表剔除。

**架构：** 在参数层新增 `dropPercentage` 数值字段，前端表单负责传入 0-100 的百分比。后端在非 dropAll 且未指定 collectionName 时，按 `globalCollectionNames` 快照计算删除数量，逐个删除成功后从 `globalCollectionNames` 移除对应名称。

**技术栈：** Vue + Element UI, Java 11, Milvus Java SDK, Maven

---

### 任务 1: 更新前端 DropCollection 表单

**文件：**
- 修改: `../test-platform-web/src/views/run/customize/components/items/dropCollectionEdit.vue`

**步骤 1: 新增表单字段**

在 `dropCollectionForm` 中添加默认值：

```js
dropPercentage: 0
```

在 Database Name 前添加数字输入：

```vue
<el-form-item label="Drop Percentage" prop="dropPercentage">
  <el-input-number v-model="dropCollectionForm.dropPercentage" :min="0" :max="100" :precision="0" style="width: 50%" id="dropPercentage" />
</el-form-item>
```

**步骤 2: 确认保存行为**

保持现有 deep watch，不额外增加按钮逻辑。

### 任务 2: 更新后端参数类

**文件：**
- 修改: `src/main/java/custom/entity/DropCollectionParams.java`

**步骤 1: 新增字段**

添加：

```java
private int dropPercentage;
```

### 任务 3: 实现百分比删除逻辑

**文件：**
- 修改: `src/main/java/custom/components/DropCollectionComp.java`

**步骤 1: 提取单个删除 helper**

新增私有方法 `dropOneCollection(String collectionName, String databaseName, List<...> resultList)`，负责清 alias、drop collection、成功后 `globalCollectionNames.remove(collectionName)`、记录结果。

**步骤 2: 修正 dropAll 全局列表更新**

`dropAll=true` 时每个 collection 成功删除后 remove 该 collection；全部成功后自然清空，不在循环内提前 clear。

**步骤 3: 添加百分比分支**

在 `dropAll=false` 且 `collectionName` 为空且 `dropPercentage > 0` 时：

```java
List<String> collectionNames = new ArrayList<>(globalCollectionNames);
int dropCount = (int) Math.ceil(collectionNames.size() * dropCollectionParams.getDropPercentage() / 100.0);
for (String collectionName : collectionNames.subList(0, dropCount)) {
    dropOneCollection(collectionName, dropCollectionParams.getDatabaseName(), dropCollectionResultList);
}
```

**步骤 4: 保留单个删除兼容行为**

如果 collectionName 非空，继续删除指定 collection；如果百分比为 0，继续删除最新 global collection。

### 任务 4: 编译验证

**文件：**
- 验证: `pom.xml`

**步骤 1: 运行 Maven 编译**

运行: `mvn -DskipTests compile`
预期: BUILD SUCCESS

**步骤 2: 如有前端工程依赖可用，运行局部 lint/build**

在 `../test-platform-web` 中根据项目脚本运行可用的 lint 或 build；若依赖缺失，说明未验证前端构建。
