package custom;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.components.InitialComp;
import custom.components.MilvusConnect;
import custom.config.CloudServiceUserInfo;
import custom.config.EnvConfig;
import custom.config.EnvEnum;
import custom.entity.InitialParams;
import custom.pojo.InstanceInfo;
import custom.utils.CloudServiceUtils;
import custom.utils.ConfigUtils;
import custom.utils.ResourceManagerServiceUtils;
import io.milvus.client.MilvusServiceClient;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.globalcluster.ClusterInfo;
import io.milvus.v2.client.globalcluster.GlobalClusterUtils;
import io.milvus.v2.client.globalcluster.GlobalTopology;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author yongpeng.li @Date 2024/6/3 17:40
 */
@Slf4j
public class BaseTest {
    public static MilvusClientV2 milvusClientV2 = null;
    public static MilvusServiceClient milvusClientV1 = null;
    public static List<String> globalCollectionNames = new ArrayList<>();
    public static String importUrl = null;
    public static int logInterval = 500;
    public static boolean isCloud = true;
    public static List<Object> recallBaseIdList = new ArrayList<>();

    public static EnvConfig envConfig = new EnvConfig();

    public static CloudServiceUserInfo cloudServiceUserInfo = new CloudServiceUserInfo();

    public static InstanceInfo newInstanceInfo = new InstanceInfo();
    /** primary 实例信息（GDN 场景下，可能与 newInstanceInfo 不同） */
    public static InstanceInfo primaryInstanceInfo = new InstanceInfo();
    public static InstanceInfo globalClusterInfo = new InstanceInfo();
    public static List<InstanceInfo> secondaryInstanceInfoList = new ArrayList<>();

    public static String redisKey;
    public static int taskId;
    // 定义根节点
    public static List<String> parentNodeName = new ArrayList<>();

    public static EnvEnum envEnum;

    public static int insertCollectionIndex = 0;
    public static int searchCollectionIndex = 0;
    public static int queryCollectionIndex = 0;
    public static int upsertCollectionIndex = 0;

    /** 按 URI 缓存 MilvusClientV2，避免重复创建 */
    private static final Map<String, MilvusClientV2> clientCache = new ConcurrentHashMap<>();

    /**
     * 根据 targetEndpoint 关键字获取对应的 MilvusClientV2。
     * <ul>
     *   <li>"" / null — 返回默认的 milvusClientV2（用户传入的 URI，可能是 primary 也可能是 secondary）</li>
     *   <li>"primary" — 连 primaryInstanceInfo.uri（GDN 场景下明确走 primary）</li>
     *   <li>"global" — 连 globalClusterInfo.uri（GDN 统一入口）</li>
     *   <li>"secondary" — 连第一个 secondary</li>
     *   <li>"secondary_0" / "secondary_1" ... — 连指定下标的 secondary</li>
     *   <li>以 "https://" 开头 — 直接连该 URI</li>
     * </ul>
     */
    public static MilvusClientV2 getMilvusClient(String targetEndpoint) {
        if (targetEndpoint == null || targetEndpoint.isEmpty()) {
            return milvusClientV2;
        }
        String uri;
        if (targetEndpoint.equalsIgnoreCase("primary")) {
            // 如果 primaryInstanceInfo 有独立 URI，走缓存创建；否则回退到默认 client
            if (primaryInstanceInfo.getUri() != null && !primaryInstanceInfo.getUri().isEmpty()) {
                uri = primaryInstanceInfo.getUri();
            } else {
                return milvusClientV2;
            }
        } else if (targetEndpoint.equalsIgnoreCase("global")) {
            uri = globalClusterInfo.getUri();
        } else if (targetEndpoint.toLowerCase().startsWith("secondary")) {
            int index = 0;
            if (targetEndpoint.contains("_")) {
                index = Integer.parseInt(targetEndpoint.substring(targetEndpoint.indexOf("_") + 1));
            }
            if (index >= secondaryInstanceInfoList.size()) {
                log.error("secondary index {} 超出范围，共 {} 个 secondary", index, secondaryInstanceInfoList.size());
                return milvusClientV2;
            }
            uri = secondaryInstanceInfoList.get(index).getUri();
        } else {
            uri = targetEndpoint;
        }

        if (uri == null || uri.isEmpty()) {
            log.warn("targetEndpoint={} 对应的 URI 为空，回退到 primary client", targetEndpoint);
            return milvusClientV2;
        }

        return clientCache.computeIfAbsent(uri, u -> {
            log.info("创建新的 MilvusClientV2，uri: {}", u);
            return MilvusConnect.createMilvusClientV2(u, newInstanceInfo.getToken());
        });
    }

    private static String normalizeHttpsEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return endpoint;
        }
        if (endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
            return endpoint;
        }
        return "https://" + endpoint;
    }

    private static String extractGlobalClusterIdFromEndpoint(String uri) {
        try {
            String host = uri.replace("https://", "").replace("http://", "");
            String gcId = host.substring(0, host.indexOf("."));
            log.info("Global Cluster ID (from URI): {}", gcId);
            return gcId;
        } catch (Exception e) {
            log.warn("从 Global Endpoint URI 提取 globalClusterId 失败: {}", e.getMessage());
            return "";
        }
    }

    private static String extractInstanceIdFromUri(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return "";
        }
        String host = uri.trim();
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        }
        int dotIndex = host.indexOf(".");
        if (dotIndex <= 0) {
            return "";
        }
        return host.substring(0, dotIndex);
    }

    private static void populateGlobalClusterInfo(String globalEndpoint, String globalClusterId, String token) {
        String normalizedGlobalEndpoint = normalizeHttpsEndpoint(globalEndpoint);
        globalClusterInfo.setUri(normalizedGlobalEndpoint);
        globalClusterInfo.setToken(token);
        if (globalClusterId != null && !globalClusterId.isEmpty()) {
            globalClusterInfo.setInstanceId(globalClusterId);
        }

        GlobalTopology topology = GlobalClusterUtils.fetchTopology(normalizedGlobalEndpoint, token);
        ClusterInfo primaryCluster = topology.getPrimary();
        String primaryEndpoint = normalizeHttpsEndpoint(primaryCluster.getEndpoint());
        primaryInstanceInfo.setUri(primaryEndpoint);
        primaryInstanceInfo.setInstanceId(primaryCluster.getClusterId());
        primaryInstanceInfo.setToken(token);
        log.info("Global Cluster primary: id={}, endpoint={}", primaryCluster.getClusterId(), primaryEndpoint);

        secondaryInstanceInfoList.clear();
        for (ClusterInfo cluster : topology.getClusters()) {
            if (!cluster.isPrimary()) {
                InstanceInfo secInfo = new InstanceInfo();
                String secEndpoint = normalizeHttpsEndpoint(cluster.getEndpoint());
                secInfo.setInstanceId(cluster.getClusterId());
                secInfo.setUri(secEndpoint);
                secInfo.setToken(token);
                secondaryInstanceInfoList.add(secInfo);
                log.info("Global Cluster secondary: id={}, endpoint={}", cluster.getClusterId(), secEndpoint);
            }
        }
    }

    private static boolean tryPopulateGlobalClusterFromInstanceUri(String uri, String token) {
        String instanceId = extractInstanceIdFromUri(uri);
        if (instanceId.isEmpty()) {
            return false;
        }
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().isEmpty()) {
            cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
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

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            taskId = Integer.parseInt(System.getProperty("taskId") == null
                    ? ""
                    : System.getProperty("taskId"));
            String env = System.getProperty("env") == null
                    ? ""
                    : System.getProperty("env");
            String uri =
                    System.getProperty("uri") == null || System.getProperty("uri").trim().equals("")
                            ? ""
                            : System.getProperty("uri");
            String token =
                    System.getProperty("token") == null || System.getProperty("token").trim().equals("")
                            ? ""
                            : System.getProperty("token");
            String initialParams =
                    System.getProperty("initial_params") == null
                            || System.getProperty("initial_params").equals("")
                            ? ""
                            : System.getProperty("initial_params");

            String customizeParams =
                    System.getProperty("customize_params") == null
                            || System.getProperty("customize_params").equals("")
                            ? ""
                            : System.getProperty("customize_params");
            log.debug("***********customizeParams*********"+customizeParams);
            log.info("========== [阶段1] 参数解析完成, taskId={}, env={}, uri={} ==========", taskId, env, uri.isEmpty() ? "(空)" : uri);
            redisKey = "customize_task_" + taskId;
            envEnum = EnvEnum.getEnvByName(env);
            log.debug("EnvEnum:" + envEnum);
            if (!env.equalsIgnoreCase("devops") && !env.equalsIgnoreCase("fouram")) {
                envConfig = ConfigUtils.providerEnvConfig(envEnum);
                log.info("当前环境信息:" + envConfig);
            }
            if (!uri.equalsIgnoreCase("")) {
                if (GlobalClusterUtils.isGlobalEndpoint(uri)) {
                    log.info("检测到 Global Endpoint: {}", uri);
                    if (token.equals("")) {
                        token = MilvusConnect.provideToken(uri);
                        log.info("查询到token:" + token);
                    }
                    populateGlobalClusterInfo(uri, extractGlobalClusterIdFromEndpoint(uri), token);
                    newInstanceInfo.setUri(primaryInstanceInfo.getUri());
                    newInstanceInfo.setInstanceId(primaryInstanceInfo.getInstanceId());
                } else {
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
                                    .anyMatch(secInfo -> instanceId.equals(secInfo.getInstanceId()));
                            log.info("输入实例属于 Global Cluster: instanceId={}, role={}", instanceId,
                                    isPrimary ? "primary" : (isSecondary ? "secondary" : "unknown"));
                        }
                    } catch (Exception e) {
                        log.warn("普通实例 URI 反查 Global Cluster 失败，按普通实例处理: {}", e.getMessage());
                    }
                }
            }
            newInstanceInfo.setToken(token);
            // primaryInstanceInfo 的 token 也同步设置（GDN 场景下需要）
            if (primaryInstanceInfo.getUri() != null && !primaryInstanceInfo.getUri().isEmpty()) {
                primaryInstanceInfo.setToken(token);
            }
            //先更新argo任务状态
            ComponentSchedule.updateArgoStatus(1);
            log.info("newInstanceInfo:" + newInstanceInfo.toString());

            log.info("========== [阶段2] 环境配置完成，准备连接Milvus ==========");
            if (newInstanceInfo.getUri() != null) {
                log.info("创建milvusClientV2，uri:" + newInstanceInfo.getUri());
                milvusClientV2 = MilvusConnect.createMilvusClientV2(newInstanceInfo.getUri(), newInstanceInfo.getToken());
                milvusClientV1 = MilvusConnect.createMilvusClientV1(newInstanceInfo.getUri(), newInstanceInfo.getToken());
                importUrl = uri;
                // 初始化环境
                InitialParams initialParamsObj = JSONObject.parseObject(initialParams, InitialParams.class);
                InitialComp.initialRunning(initialParamsObj);
            }
            // 日志级别设置：只对本项目 custom.* 包生效，第三方库固定 INFO 防止刷屏
            if (!initialParams.isEmpty()) {
                InitialParams initP = JSONObject.parseObject(initialParams, InitialParams.class);
                if (initP.getLogLevel() != null && !initP.getLogLevel().isEmpty()) {
                    org.apache.log4j.Level level = org.apache.log4j.Level.toLevel(initP.getLogLevel(), org.apache.log4j.Level.INFO);
                    org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.INFO);
                    org.apache.log4j.Logger.getLogger("custom").setLevel(level);
                    log.info("本项目日志级别设置为: {}（第三方库固定 INFO）", level);
                }
            }
            log.info("========== [阶段3] Milvus连接完成，开始执行调度 ==========");
//        // 自动调度
            List<JSONObject> results = ComponentSchedule.runningSchedule(customizeParams);
            // 检查是否有步骤失败（result为exception）
            boolean hasFailed = false;
            for (JSONObject result : results) {
                String resultStr = result.toJSONString();
                if (resultStr.contains("\"result\":\"exception\"")) {
                    hasFailed = true;
                    break;
                }
            }
            ComponentSchedule.updateCaseStatus(hasFailed ? -1 : 10);
            if (hasFailed) {
                log.error("任务存在失败步骤，退出码设为1");
                exitCode = 1;
            }
        } catch (Exception e) {
            log.error("Task execution failed: {} - {}", e.getClass().getName(), e.getMessage());
            log.error("异常堆栈:", e);
            // 打印完整异常链
            Throwable cause = e.getCause();
            int depth = 1;
            while (cause != null) {
                log.error("  Caused by (depth={}): {} - {}", depth, cause.getClass().getName(), cause.getMessage());
                cause = cause.getCause();
                depth++;
            }
            exitCode = 1;
            try {
                ComponentSchedule.updateCaseStatus(-1);
            } catch (Exception updateEx) {
                log.error("更新任务状态也失败了: {}", updateEx.getMessage());
            }
        } finally {
            if (milvusClientV2 != null) {
                try { milvusClientV2.close(); } catch (Exception ignored) {}
            }
            if (milvusClientV1 != null) {
                try { milvusClientV1.close(); } catch (Exception ignored) {}
            }
            System.exit(exitCode);
        }
    }
}
