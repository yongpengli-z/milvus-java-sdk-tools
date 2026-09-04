# 实例 URI 反查 Global Cluster 实现计划

**目标：** 支持传入普通实例 URI 时自动判断该实例是 primary 还是 secondary，并反查 global endpoint 后填充 `globalClusterInfo`、`primaryInstanceInfo`、`secondaryInstanceInfoList`。

**架构：** 保留现有 global endpoint 输入路径；新增普通实例 URI 的 GDN 反查路径。普通 URI 先提取 instanceId，通过 RM `describeInstance` 获取 `GlobalClusterId`，再通过 cloud-service 查询 global endpoint，最后调用现有 topology API 获取 primary/secondary 全量拓扑并填充全局变量。若普通实例不属于 Global Cluster，则保持现有普通实例行为。

**技术栈：** Java 11、Maven、Fastjson、现有 `ResourceManagerServiceUtils`、现有 `GlobalClusterUtils`。

---

### 任务 1: 抽取 BaseTest 中的 GDN topology 填充逻辑

**文件：**
- 修改: `src/main/java/custom/BaseTest.java:144-202`

**步骤 1: 新增 endpoint 规范化辅助方法**

在 `BaseTest` 类中新增私有静态方法：

```java
private static String normalizeHttpsEndpoint(String endpoint) {
    if (endpoint == null || endpoint.isEmpty()) {
        return endpoint;
    }
    return endpoint.startsWith("https://") || endpoint.startsWith("http://") ? endpoint : "https://" + endpoint;
}
```

**步骤 2: 新增 GlobalClusterId 提取辅助方法**

```java
private static String extractGlobalClusterIdFromEndpoint(String uri) {
    try {
        String host = uri.replace("https://", "").replace("http://", "");
        return host.substring(0, host.indexOf("."));
    } catch (Exception e) {
        log.warn("从 Global Endpoint URI 提取 globalClusterId 失败: {}", e.getMessage());
        return "";
    }
}
```

**步骤 3: 新增 topology 填充方法**

```java
private static void populateGlobalClusterInfo(String globalEndpoint, String globalClusterId, String token) {
    globalEndpoint = normalizeHttpsEndpoint(globalEndpoint);
    globalClusterInfo.setUri(globalEndpoint);
    if (globalClusterId != null && !globalClusterId.isEmpty()) {
        globalClusterInfo.setInstanceId(globalClusterId);
    }

    GlobalTopology topology = GlobalClusterUtils.fetchTopology(globalEndpoint, token);
    ClusterInfo primaryCluster = topology.getPrimary();
    String primaryEndpoint = normalizeHttpsEndpoint(primaryCluster.getEndpoint());

    primaryInstanceInfo.setUri(primaryEndpoint);
    primaryInstanceInfo.setInstanceId(primaryCluster.getClusterId());
    primaryInstanceInfo.setToken(token);

    secondaryInstanceInfoList.clear();
    for (ClusterInfo cluster : topology.getClusters()) {
        if (!cluster.isPrimary()) {
            InstanceInfo secInfo = new InstanceInfo();
            secInfo.setInstanceId(cluster.getClusterId());
            secInfo.setUri(normalizeHttpsEndpoint(cluster.getEndpoint()));
            secInfo.setToken(token);
            secondaryInstanceInfoList.add(secInfo);
        }
    }
}
```

**步骤 4: 替换现有 global endpoint 分支重复代码**

将 `BaseTest.java:145-189` 中手写 topology 解析替换为：

```java
log.info("检测到 Global Endpoint: {}", uri);
if (token.equals("")) {
    token = MilvusConnect.provideToken(uri);
    log.info("查询到token:" + token);
}
populateGlobalClusterInfo(uri, extractGlobalClusterIdFromEndpoint(uri), token);
ClusterInfo primaryCluster = GlobalClusterUtils.fetchTopology(uri, token).getPrimary();
newInstanceInfo.setUri(normalizeHttpsEndpoint(primaryCluster.getEndpoint()));
newInstanceInfo.setInstanceId(primaryCluster.getClusterId());
```

实现时避免重复 fetch topology：可以让 `populateGlobalClusterInfo` 返回 `primaryInstanceInfo` 或者在调用后从 `primaryInstanceInfo` 复制到 `newInstanceInfo`。

**步骤 5: 编译验证**

运行: `mvn -DskipTests package`
预期: BUILD SUCCESS。

---

### 任务 2: 普通实例 URI 提取 instanceId 并反查 GDN

**文件：**
- 修改: `src/main/java/custom/BaseTest.java:190-202`
- 使用: `src/main/java/custom/utils/ResourceManagerServiceUtils.java:548-585`

**步骤 1: 新增实例 ID 提取方法**

```java
private static String extractInstanceIdFromUri(String uri) {
    if (uri == null || uri.isEmpty()) {
        return "";
    }
    String value = uri.trim();
    if (value.startsWith("https://")) {
        value = value.substring("https://".length());
    } else if (value.startsWith("http://")) {
        value = value.substring("http://".length());
    }
    int dotIndex = value.indexOf(".");
    return dotIndex > 0 ? value.substring(0, dotIndex) : "";
}
```

对示例 URI `https://in01-5eded6d66619e55.aws-us-west-2.vectordb-uat3.zillizcloud.com:19531`，该方法返回 `in01-5eded6d66619e55`。

**步骤 2: 新增普通实例 URI 的 GDN 解析方法**

```java
private static boolean tryPopulateGlobalClusterFromInstanceUri(String uri, String token) {
    String instanceId = extractInstanceIdFromUri(uri);
    if (instanceId.isEmpty()) {
        return false;
    }

    String globalClusterId = ResourceManagerServiceUtils.getGlobalClusterId(instanceId);
    if (globalClusterId == null || globalClusterId.isEmpty()) {
        return false;
    }

    String globalEndpoint = ResourceManagerServiceUtils.describeGlobalClusterEndpoint(globalClusterId);
    if (globalEndpoint == null || globalEndpoint.isEmpty()) {
        return false;
    }

    populateGlobalClusterInfo(globalEndpoint, globalClusterId, token);
    return true;
}
```

**步骤 3: 修改普通 URI 分支**

把现有普通 URI 分支调整为：

```java
newInstanceInfo.setUri(uri);
String instanceId = extractInstanceIdFromUri(uri);
if (!instanceId.isEmpty()) {
    log.info("instance-id:" + instanceId);
    newInstanceInfo.setInstanceId(instanceId);
}
if (token.equals("")) {
    token = MilvusConnect.provideToken(uri);
    log.info("查询到token:" + token);
}
try {
    boolean globalResolved = tryPopulateGlobalClusterFromInstanceUri(uri, token);
    if (globalResolved) {
        boolean isPrimary = instanceId.equals(primaryInstanceInfo.getInstanceId());
        boolean isSecondary = secondaryInstanceInfoList.stream()
                .anyMatch(sec -> instanceId.equals(sec.getInstanceId()));
        log.info("输入实例属于 Global Cluster: instanceId={}, role={}", instanceId,
                isPrimary ? "primary" : (isSecondary ? "secondary" : "unknown"));
    }
} catch (Exception e) {
    log.warn("普通实例 URI 反查 Global Cluster 失败，按普通实例处理: {}", e.getMessage());
}
```

**步骤 4: 保持非 GDN 普通实例兼容**

如果 `getGlobalClusterId(instanceId)` 返回空，不能抛错；继续按现有普通实例逻辑执行。

**步骤 5: 编译验证**

运行: `mvn -DskipTests package`
预期: BUILD SUCCESS。

---

### 任务 3: 确认 primary/secondary 填充结果

**文件：**
- 修改: `src/main/java/custom/BaseTest.java`

**步骤 1: 确认传 primary URI 的结果**

输入示例：

```bash
java -Denv=<env> -Duri=https://in01-5eded6d66619e55.aws-us-west-2.vectordb-uat3.zillizcloud.com:19531 -Dtoken=<token> -jar target/milvus-java-sdk-toos-1.0.jar
```

预期日志：
- `instance-id:in01-5eded6d66619e55`
- `输入实例属于 Global Cluster: instanceId=in01-5eded6d66619e55, role=primary`
- `globalClusterInfo.uri` 为 global endpoint
- `primaryInstanceInfo.uri` 为 primary URI
- `secondaryInstanceInfoList` 包含所有 secondary。

**步骤 2: 确认传 secondary URI 的结果**

输入 secondary URI。

预期日志：
- `role=secondary`
- `primaryInstanceInfo` 仍填 primary
- `secondaryInstanceInfoList` 包含输入 secondary 以及其他 secondary。

**步骤 3: 确认传非 GDN 普通 URI 的结果**

输入普通 standalone/非 GDN URI。

预期：
- 不填 `globalClusterInfo.uri`
- 不填 `primaryInstanceInfo`
- `secondaryInstanceInfoList` 为空
- 原有任务仍按 `newInstanceInfo.uri` 连接。

---

### 任务 4: 最终验证

**文件：**
- 修改: `src/main/java/custom/BaseTest.java`

**步骤 1: 编译**

运行: `mvn -DskipTests package`
预期: BUILD SUCCESS。

**步骤 2: 检查关键日志**

运行传入 primary URI 的真实任务，确认日志包含：

```text
输入实例属于 Global Cluster: instanceId=..., role=primary
Global Cluster primary: id=..., endpoint=...
Global Cluster secondary: id=..., endpoint=...
```

**步骤 3: 检查变量使用方**

确认以下使用方可以读取新填充的值：
- `src/main/java/custom/BaseTest.java:83-88` 的 `connectTarget=global`
- `src/main/java/custom/components/CreateSecondaryComp.java:48-52` 的 `globalClusterInfo.instanceId`

**步骤 4: 不提交除非用户要求**

不自动 `git commit`。用户明确要求提交时再提交。
