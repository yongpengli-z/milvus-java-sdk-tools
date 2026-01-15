package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import custom.entity.HelmComponentConfig;
import custom.entity.HelmCreateInstanceParams;
import custom.entity.HelmDependencyConfig;
import custom.entity.HelmResourceConfig;
import custom.entity.result.CommonResult;
import custom.entity.result.HelmCreateInstanceResult;
import custom.entity.result.ResultEnum;
import custom.utils.HelmUtils;
import custom.utils.KubernetesUtils;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.*;
import static custom.BaseTest.envEnum;

/**
 * Helm 方式创建 Milvus 实例组件。
 * <p>
 * 通过 Helm Chart 在 Kubernetes 中部署 Milvus 实例。
 */
@Slf4j
public class HelmCreateInstanceComp {

    /**
     * 创建 Milvus 实例
     *
     * @param params 创建参数
     * @return 创建结果
     */
    public static HelmCreateInstanceResult createInstance(HelmCreateInstanceParams params) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("Starting Helm create instance...");

        try {
            // 1. 初始化 K8s 客户端
            log.info("Step 1: Initializing Kubernetes client...");
            // kubeconfig 路径由 EnvEnum 控制
            String kubeconfigPath = envEnum != null ? envEnum.kubeConfig : null;
            log.info("Using kubeconfig: " + kubeconfigPath);
            CoreV1Api coreApi = KubernetesUtils.createCoreV1Api(kubeconfigPath);

            // 2. 获取命名空间（默认使用 qa）
            String namespace = params.getNamespace();
            if (namespace == null || namespace.isEmpty()) {
                namespace = "chaos-testing";
            }
            log.info("Using namespace: " + namespace);

            // 3. 获取本地 Helm Chart 路径
            String helmChartPath = envEnum != null ? envEnum.helmChartPath : null;
            if (helmChartPath == null || helmChartPath.isEmpty()) {
                return buildFailResult("Helm chart path is not configured in EnvEnum", startTime);
            }
            log.info("Using local Helm chart: " + helmChartPath);

            // 4. 构建 Helm Values (--set 参数)
            log.info("Step 4: Building Helm values...");
            Map<String, String> setValues = buildHelmValues(params);
            log.info("Helm values: " + setValues);

            // 5. 检查是否已存在同名 Release
            String releaseName = params.getReleaseName();
            if (HelmUtils.releaseExists(releaseName, namespace, kubeconfigPath)) {
                return buildFailResult("Helm release already exists: " + releaseName, startTime);
            }

            // 6. 执行 Helm Install（使用本地 Chart）
            log.info("Step 5: Executing Helm install...");
            int timeout = params.getWaitTimeoutMinutes();
            if (timeout <= 0) {
                timeout = 30;
            }

            HelmUtils.CommandResult installResult = HelmUtils.install(
                    releaseName,
                    helmChartPath,  // 使用本地 Chart 路径
                    namespace,
                    false,  // 命名空间已预先创建，不需要自动创建
                    null,   // 本地 Chart 不需要版本
                    setValues,
                    kubeconfigPath,
                    timeout
            );

            if (!installResult.isSuccess()) {
                log.error("Helm install failed: " + installResult.getStderr());
                return buildFailResult("Helm install failed: " + installResult.getStderr(), startTime);
            }
            log.info("Helm install completed successfully");

            // 7. 等待 Pod Ready
            log.info("Step 6: Waiting for pods to be ready...");
            String labelSelector = "app.kubernetes.io/instance=" + releaseName;
            List<KubernetesUtils.PodStatus> podStatuses = KubernetesUtils.waitForPodsReady(
                    coreApi,
                    namespace,
                    labelSelector,
                    timeout
            );

            // 检查是否所有 Pod 都 Ready
            boolean allReady = podStatuses.stream().allMatch(KubernetesUtils.PodStatus::isReady);
            if (!allReady) {
                log.warn("Not all pods are ready");
            }

            // 8. 获取连接地址（LoadBalancer）
            log.info("Step 7: Getting connection address...");
            String uri = "";

            // 通过标签选择器和 releaseName 查找服务
            // 优先级：releaseName-milvus > releaseName > 任何带 19530 端口的服务
            String lbIp = KubernetesUtils.getLoadBalancerIpByLabel(coreApi, namespace, releaseName, labelSelector, 10);
            if (!lbIp.isEmpty()) {
                uri = "http://" + lbIp + ":19530";
            }

            log.info("Milvus URI: " + uri);

            // 9. 初始化 Milvus 客户端（Helm 部署的实例不需要认证）
            if (!uri.isEmpty()) {
                log.info("Step 8: Initializing Milvus client...");
                try {
                    newInstanceInfo.setUri(uri);
                    newInstanceInfo.setToken("");
                    newInstanceInfo.setInstanceName(releaseName);
                    newInstanceInfo.setInstanceId(releaseName);

                    milvusClientV2 = MilvusConnect.createMilvusClientV2(uri, "");
                    milvusClientV1 = MilvusConnect.createMilvusClientV1(uri, "");
                    log.info("Milvus client initialized successfully");
                } catch (Exception e) {
                    log.error("Failed to initialize Milvus client: " + e.getMessage());
                }
            }

            // 10. 构建返回结果
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            String milvusMode = params.getMilvusMode();

            return HelmCreateInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message("Milvus instance created successfully")
                            .build())
                    .uri(uri)
                    .releaseName(releaseName)
                    .namespace(namespace)
                    .milvusMode(milvusMode != null ? milvusMode : "standalone")
                    .milvusVersion(params.getMilvusImageTag() != null ? params.getMilvusImageTag() : "default")
                    .deploymentCostSeconds(costSeconds)
                    .podStatus(podStatuses)
                    .build();

        } catch (Exception e) {
            log.error("Failed to create Milvus instance", e);
            return buildFailResult("Exception: " + e.getMessage(), startTime);
        }
    }

    /**
     * 构建 Helm Values Map
     */
    private static Map<String, String> buildHelmValues(HelmCreateInstanceParams params) {
        Map<String, String> values = new HashMap<>();

        // Milvus 模式
        String milvusMode = params.getMilvusMode();
        if (milvusMode == null || milvusMode.isEmpty()) {
            milvusMode = "standalone";
        }

        if ("standalone".equalsIgnoreCase(milvusMode)) {
            values.put("cluster.enabled", "false");
            values.put("standalone.enabled", "true");
            // Standalone 模式禁用 Pulsar 和 Kafka
            values.put("pulsar.enabled", "false");
            values.put("pulsarv3.enabled", "false");
            values.put("kafka.enabled", "false");
        } else if ("cluster".equalsIgnoreCase(milvusMode)) {
            values.put("cluster.enabled", "true");
            values.put("standalone.enabled", "false");
        }

        // 镜像配置
        if (params.getMilvusImageTag() != null && !params.getMilvusImageTag().isEmpty()) {
            values.put("image.tag", params.getMilvusImageTag());
        }

        // Service 配置（默认 LoadBalancer）
        values.put("service.type", "LoadBalancer");

        // MinIO 配置
        HelmDependencyConfig minioConfig = params.getMinioConfig();
        if (minioConfig != null) {
            if (minioConfig.isUseExternal()) {
                values.put("minio.enabled", "false");
                values.put("externalS3.enabled", "true");
                if (minioConfig.getExternalEndpoints() != null && !minioConfig.getExternalEndpoints().isEmpty()) {
                    values.put("externalS3.host", minioConfig.getExternalEndpoints());
                }
                if (minioConfig.getAccessKey() != null && !minioConfig.getAccessKey().isEmpty()) {
                    values.put("externalS3.accessKey", minioConfig.getAccessKey());
                }
                if (minioConfig.getSecretKey() != null && !minioConfig.getSecretKey().isEmpty()) {
                    values.put("externalS3.secretKey", minioConfig.getSecretKey());
                }
                if (minioConfig.getBucketName() != null && !minioConfig.getBucketName().isEmpty()) {
                    values.put("externalS3.bucketName", minioConfig.getBucketName());
                }
                if (minioConfig.getRootPath() != null && !minioConfig.getRootPath().isEmpty()) {
                    values.put("externalS3.rootPath", minioConfig.getRootPath());
                }
            } else {
                values.put("minio.enabled", String.valueOf(minioConfig.isEnabled()));
                // MinIO 副本数和模式
                int minioReplicas = minioConfig.getReplicaCount();
                if (minioReplicas <= 0) {
                    minioReplicas = 1;
                }
                if (minioReplicas == 1) {
                    values.put("minio.mode", "standalone");
                } else {
                    values.put("minio.mode", "distributed");
                    values.put("minio.replicas", String.valueOf(minioReplicas));
                }
                if (minioConfig.getStorageSize() != null && !minioConfig.getStorageSize().isEmpty()) {
                    values.put("minio.persistence.size", minioConfig.getStorageSize());
                }
                if (minioConfig.getStorageClassName() != null && !minioConfig.getStorageClassName().isEmpty()) {
                    values.put("minio.persistence.storageClass", minioConfig.getStorageClassName());
                }
            }
        } else {
            // 默认单副本 MinIO
            values.put("minio.mode", "standalone");
        }

        // etcd 配置
        // 注意：etcd 副本数通常为奇数（1, 3, 5）以满足 Raft 协议
        HelmDependencyConfig etcdConfig = params.getEtcdConfig();
        if (etcdConfig != null) {
            if (etcdConfig.isUseExternal()) {
                values.put("etcd.enabled", "false");
                values.put("externalEtcd.enabled", "true");
                if (etcdConfig.getExternalEndpoints() != null && !etcdConfig.getExternalEndpoints().isEmpty()) {
                    values.put("externalEtcd.endpoints", etcdConfig.getExternalEndpoints());
                }
            } else {
                values.put("etcd.enabled", String.valueOf(etcdConfig.isEnabled()));
                int etcdReplicas = etcdConfig.getReplicaCount();
                if (etcdReplicas <= 0) {
                    etcdReplicas = "standalone".equalsIgnoreCase(milvusMode) ? 1 : 3;
                }
                values.put("etcd.replicaCount", String.valueOf(etcdReplicas));
                if (etcdConfig.getStorageSize() != null && !etcdConfig.getStorageSize().isEmpty()) {
                    values.put("etcd.persistence.size", etcdConfig.getStorageSize());
                }
                if (etcdConfig.getStorageClassName() != null && !etcdConfig.getStorageClassName().isEmpty()) {
                    values.put("etcd.persistence.storageClass", etcdConfig.getStorageClassName());
                }
            }
        } else {
            // 默认配置：Standalone 1 副本，Cluster 3 副本
            if ("standalone".equalsIgnoreCase(milvusMode)) {
                values.put("etcd.replicaCount", "1");
            }
        }

        // 消息队列配置（仅 Cluster 模式）
        // 使用 pulsarv3 替代旧版 pulsar
        if ("cluster".equalsIgnoreCase(milvusMode)) {
            HelmDependencyConfig pulsarConfig = params.getPulsarConfig();
            HelmDependencyConfig kafkaConfig = params.getKafkaConfig();

            // 禁用旧版 pulsar，使用 pulsarv3
            values.put("pulsar.enabled", "false");

            // 如果配置了 Kafka，使用 Kafka 替代 Pulsar
            if (kafkaConfig != null && (kafkaConfig.isUseExternal() || kafkaConfig.isEnabled())) {
                values.put("pulsarv3.enabled", "false");
                values.put("kafka.enabled", "true");
                if (kafkaConfig.isUseExternal()) {
                    values.put("kafka.enabled", "false");
                    values.put("externalKafka.enabled", "true");
                    if (kafkaConfig.getExternalEndpoints() != null) {
                        values.put("externalKafka.brokerList", kafkaConfig.getExternalEndpoints());
                    }
                }
            } else if (pulsarConfig != null) {
                // 使用 pulsarv3
                values.put("kafka.enabled", "false");
                if (pulsarConfig.isUseExternal()) {
                    values.put("pulsarv3.enabled", "false");
                    values.put("externalPulsar.enabled", "true");
                    if (pulsarConfig.getExternalEndpoints() != null) {
                        values.put("externalPulsar.host", pulsarConfig.getExternalEndpoints());
                    }
                } else {
                    values.put("pulsarv3.enabled", String.valueOf(pulsarConfig.isEnabled()));
                }
            } else {
                // 默认启用 pulsarv3，禁用 kafka
                values.put("pulsarv3.enabled", "true");
                values.put("kafka.enabled", "false");
            }
        }

        // 资源配置（Standalone 模式）
        HelmResourceConfig resources = params.getResources();
        if (resources != null && "standalone".equalsIgnoreCase(milvusMode)) {
            if (resources.getCpuRequest() != null) {
                values.put("standalone.resources.requests.cpu", resources.getCpuRequest());
            }
            if (resources.getCpuLimit() != null) {
                values.put("standalone.resources.limits.cpu", resources.getCpuLimit());
            }
            if (resources.getMemoryRequest() != null) {
                values.put("standalone.resources.requests.memory", resources.getMemoryRequest());
            }
            if (resources.getMemoryLimit() != null) {
                values.put("standalone.resources.limits.memory", resources.getMemoryLimit());
            }
        }

        // Cluster 模式组件配置
        if ("cluster".equalsIgnoreCase(milvusMode)) {
            // 获取部署架构模式
            String deployArchitecture = params.getDeployArchitecture();
            boolean isStreamingArch = "streaming".equalsIgnoreCase(deployArchitecture);

            // 默认启用 mixCoordinator，禁用单独的 Coordinator
            // 这样只会部署一个 mixCoordinator Pod，而不是 4 个独立的 Coordinator Pod
            values.put("mixCoordinator.enabled", "true");
            values.put("rootCoordinator.enabled", "false");
            values.put("queryCoordinator.enabled", "false");
            values.put("indexCoordinator.enabled", "false");
            values.put("dataCoordinator.enabled", "false");
            log.info("Using mixCoordinator mode (single coordinator pod)");

            // Proxy 配置
            HelmComponentConfig proxyConfig = params.getProxyConfig();
            if (proxyConfig != null) {
                applyComponentConfig(values, "proxy", proxyConfig);
            }

            // Query Node 配置
            HelmComponentConfig queryNodeConfig = params.getQueryNodeConfig();
            if (queryNodeConfig != null) {
                applyComponentConfig(values, "queryNode", queryNodeConfig);
            }

            // Data Node 配置
            HelmComponentConfig dataNodeConfig = params.getDataNodeConfig();
            if (dataNodeConfig != null) {
                applyComponentConfig(values, "dataNode", dataNodeConfig);
            }

            // Mix Coordinator 配置（如果用户有自定义配置）
            HelmComponentConfig mixCoordinatorConfig = params.getMixCoordinatorConfig();
            if (mixCoordinatorConfig != null) {
                applyComponentConfig(values, "mixCoordinator", mixCoordinatorConfig);
            }

            if (isStreamingArch) {
                // Streaming 架构（Milvus 2.6+）：启用 streamingNode，禁用 indexNode
                log.info("Using streaming architecture (Milvus 2.6+)");

                // 禁用 indexNode
                values.put("indexNode.enabled", "false");

                // Streaming Node 配置
                HelmComponentConfig streamingNodeConfig = params.getStreamingNodeConfig();
                if (streamingNodeConfig != null) {
                    applyComponentConfig(values, "streamingNode", streamingNodeConfig);
                }
            } else {
                // 默认架构（Milvus ≤2.5）：启用 indexNode，禁用 streamingNode
                log.info("Using default architecture (Milvus ≤2.5)");

                // Index Node 配置
                HelmComponentConfig indexNodeConfig = params.getIndexNodeConfig();
                if (indexNodeConfig != null) {
                    applyComponentConfig(values, "indexNode", indexNodeConfig);
                }

                // 禁用 streamingNode
                values.put("streamingNode.enabled", "false");
            }
        }

        // 自定义 Values（JSON 格式）
        String customValues = params.getCustomValues();
        if (customValues != null && !customValues.isEmpty()) {
            try {
                JSONObject customJson = JSON.parseObject(customValues);
                flattenJson("", customJson, values);
            } catch (Exception e) {
                log.warn("Failed to parse custom values: " + e.getMessage());
            }
        }

        return values;
    }

    /**
     * 应用组件配置到 Helm Values
     */
    private static void applyComponentConfig(Map<String, String> values, String componentName, HelmComponentConfig config) {
        if (config.getReplicas() > 0) {
            values.put(componentName + ".replicas", String.valueOf(config.getReplicas()));
        }
        if (config.getCpuRequest() != null && !config.getCpuRequest().isEmpty()) {
            values.put(componentName + ".resources.requests.cpu", config.getCpuRequest());
        }
        if (config.getCpuLimit() != null && !config.getCpuLimit().isEmpty()) {
            values.put(componentName + ".resources.limits.cpu", config.getCpuLimit());
        }
        if (config.getMemoryRequest() != null && !config.getMemoryRequest().isEmpty()) {
            values.put(componentName + ".resources.requests.memory", config.getMemoryRequest());
        }
        if (config.getMemoryLimit() != null && !config.getMemoryLimit().isEmpty()) {
            values.put(componentName + ".resources.limits.memory", config.getMemoryLimit());
        }
        if (config.getDiskSize() != null && !config.getDiskSize().isEmpty()) {
            values.put(componentName + ".disk.size", config.getDiskSize());
        }
    }

    /**
     * 将嵌套 JSON 展平为 dot-notation 键值对
     */
    private static void flattenJson(String prefix, JSONObject json, Map<String, String> result) {
        for (String key : json.keySet()) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                flattenJson(fullKey, (JSONObject) value, result);
            } else {
                result.put(fullKey, String.valueOf(value));
            }
        }
    }

    /**
     * 构建失败结果
     */
    private static HelmCreateInstanceResult buildFailResult(String message, LocalDateTime startTime) {
        int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        return HelmCreateInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message(message)
                        .build())
                .deploymentCostSeconds(costSeconds)
                .build();
    }
}
