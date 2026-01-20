package custom.components;

import custom.common.ComponentSchedule;
import custom.common.InstanceStatusEnum;
import custom.entity.HelmComponentConfig;
import custom.entity.HelmConfigItem;
import custom.entity.HelmCreateInstanceParams;
import custom.entity.HelmDependencyConfig;
import custom.entity.HelmResourceConfig;
import custom.entity.WoodpeckerConfig;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        String releaseName = params.getReleaseName();
        String imageTag = params.getMilvusImageTag() != null ? params.getMilvusImageTag() : "default";

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
                namespace = "milvus-qtp";
            }
            log.info("Using namespace: " + namespace);

            // 3. 获取本地 Helm Chart 路径
            String helmChartPath = envEnum != null ? envEnum.helmChartPath : null;
            if (helmChartPath == null || helmChartPath.isEmpty()) {
                ComponentSchedule.initInstanceStatus(releaseName, "--", imageTag, InstanceStatusEnum.CREATE_FAILED.code);
                return buildFailResult("Helm chart path is not configured in EnvEnum", startTime, releaseName);
            }
            log.info("Using local Helm chart: " + helmChartPath);

            // 4. 构建 Helm Values (--set 参数)
            log.info("Step 4: Building Helm values...");
            Map<String, String> setValues = buildHelmValues(params);
            log.info("Helm values: " + setValues);

            // 5. 检查是否已存在同名 Release
            if (HelmUtils.releaseExists(releaseName, namespace, kubeconfigPath)) {
                ComponentSchedule.initInstanceStatus(releaseName, "--", imageTag, InstanceStatusEnum.CREATE_FAILED.code);
                return buildFailResult("Helm release already exists: " + releaseName, startTime, releaseName);
            }

            // 上报实例创建中状态（使用 "creating..." 作为占位符，避免前端因空字符串不展示）
            ComponentSchedule.initInstanceStatus(releaseName, "creating...", imageTag, InstanceStatusEnum.CREATING.code);

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
                ComponentSchedule.updateInstanceStatus(releaseName, "--", imageTag, InstanceStatusEnum.CREATE_FAILED.code);
                return buildFailResult("Helm install failed: " + installResult.getStderr(), startTime, releaseName);
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

            // 10. 构建 Pod 信息字符串（格式：podName1:endpoint1,podName2:endpoint2,...）
            String instanceUri = buildInstanceUri(podStatuses, uri);
            log.info("Instance URI with pods: " + instanceUri);

            // 11. 上报实例创建成功状态
            ComponentSchedule.updateInstanceStatus(releaseName, instanceUri, imageTag, InstanceStatusEnum.RUNNING.code);
            log.info("Instance status reported successfully");

            // 12. 构建返回结果
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            String milvusMode = params.getMilvusMode();
            LocalDateTime createTime = LocalDateTime.now();
            int useHours = params.getUseHours();
            LocalDateTime expireTime = null;
            if (useHours > 0) {
                expireTime = createTime.plusHours(useHours);
            }

            return HelmCreateInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message("Milvus instance created successfully")
                            .build())
                    .uri(uri)
                    .releaseName(releaseName)
                    .namespace(namespace)
                    .milvusMode(milvusMode != null ? milvusMode : "standalone")
                    .milvusVersion(imageTag)
                    .deploymentCostSeconds(costSeconds)
                    .podStatus(podStatuses)
                    .createTime(createTime)
                    .useHours(useHours)
                    .expireTime(expireTime)
                    .build();

        } catch (Exception e) {
            log.error("Failed to create Milvus instance", e);
            ComponentSchedule.updateInstanceStatus(releaseName, "--", imageTag, InstanceStatusEnum.CREATE_FAILED.code);
            return buildFailResult("Exception: " + e.getMessage(), startTime, releaseName);
        }
    }

    /**
     * 构建实例 URI 字符串，包含 Pod 名称和端点信息
     * <p>
     * 格式：uri|podName1:endpoint1,podName2:endpoint2,...
     * 示例：http://10.0.0.1:19530|my-milvus-standalone-0:10.0.0.2:19530,my-milvus-etcd-0:10.0.0.3:2379
     *
     * @param podStatuses Pod 状态列表
     * @param uri         Milvus 连接 URI
     * @return 格式化后的实例 URI 字符串
     */
    private static String buildInstanceUri(List<KubernetesUtils.PodStatus> podStatuses, String uri) {
        if (podStatuses == null || podStatuses.isEmpty()) {
            return uri;
        }

        // 构建 Pod 信息：podName:endpoint
        String podInfoStr = podStatuses.stream()
                .map(pod -> {
                    String podName = pod.getName();
                    String endpoint = pod.getEndpoint();
                    if (endpoint != null && !endpoint.isEmpty()) {
                        return podName + ":" + endpoint;
                    } else if (pod.getPodIp() != null && !pod.getPodIp().isEmpty()) {
                        // 如果没有 endpoint，使用 podIp:19530 作为默认
                        return podName + ":" + pod.getPodIp() + ":19530";
                    }
                    return podName;
                })
                .collect(Collectors.joining(","));

        // 格式：uri|podInfo
        if (uri != null && !uri.isEmpty()) {
            return uri + "|" + podInfoStr;
        }
        return podInfoStr;
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
        // 注意：Milvus Helm Chart 使用 image.all.tag 而不是 image.tag
        if (params.getMilvusImageTag() != null && !params.getMilvusImageTag().isEmpty()) {
            values.put("image.all.tag", params.getMilvusImageTag());
        }

        // Service 配置（默认 LoadBalancer）
        values.put("service.type", "LoadBalancer");

        // MinIO 配置
        HelmDependencyConfig minioConfig = params.getMinioConfig();
        if (minioConfig != null) {
            if (minioConfig.isUseExternal()) {
                // 禁用内置 MinIO，启用外部 S3 存储
                values.put("minio.enabled", "false");
                values.put("externalS3.enabled", "true");
                
                // 根据 envEnum 选择 cloudProvider
                String cloudProvider = getCloudProviderFromEnv();
                values.put("externalS3.cloudProvider", cloudProvider);
                log.info("Using cloudProvider: " + cloudProvider + " based on envEnum: " + (envEnum != null ? envEnum.name() : "null"));
                
                // 根据云平台自动设置 port 和 useSSL
                int port = getDefaultPort(cloudProvider);
                boolean useSSL = getDefaultUseSSL(cloudProvider);
                values.put("externalS3.port", String.valueOf(port));
                values.put("externalS3.useSSL", String.valueOf(useSSL));
                
                // Host 配置
                if (minioConfig.getExternalEndpoints() != null && !minioConfig.getExternalEndpoints().isEmpty()) {
                    values.put("externalS3.host", minioConfig.getExternalEndpoints());
                }
                
                // 认证配置：IAM 或 AK/SK
                if (minioConfig.isUseIAM()) {
                    // 使用 IAM 认证（AWS IAM Role / GCP Workload Identity / Azure Managed Identity）
                    values.put("externalS3.useIAM", "true");
                    log.info("Using IAM authentication for externalS3");
                    
                    // IAM Endpoint（可选）
                    if (minioConfig.getIamEndpoint() != null && !minioConfig.getIamEndpoint().isEmpty()) {
                        values.put("externalS3.iamEndpoint", minioConfig.getIamEndpoint());
                    }
                    
                    // Azure Workload Identity 默认配置
                    if ("azure".equalsIgnoreCase(cloudProvider)) {
                        values.put("serviceAccount.create", "true");
                        values.put("serviceAccount.name", "workload-identity-sa");
                        values.put("serviceAccount.annotations.azure\\.workload\\.identity/client-id", "2469e573-0c2a-4818-9eb4-ad20ccfffdfe");
                        log.info("Azure Workload Identity configured with default settings");
                        values.put("externalS3.accessKey", "buckettestazure");
                    }
                } else {
                    // 使用 AK/SK 认证
                    values.put("externalS3.useIAM", "false");
                    if (minioConfig.getAccessKey() != null && !minioConfig.getAccessKey().isEmpty()) {
                        values.put("externalS3.accessKey", minioConfig.getAccessKey());
                    }
                    if (minioConfig.getSecretKey() != null && !minioConfig.getSecretKey().isEmpty()) {
                        values.put("externalS3.secretKey", minioConfig.getSecretKey());
                    }
                }
                
                // Region 配置（某些云平台需要）
                if (minioConfig.getRegion() != null && !minioConfig.getRegion().isEmpty()) {
                    values.put("externalS3.region", minioConfig.getRegion());
                }
                
                // Bucket 和 RootPath 配置
                if (minioConfig.getBucketName() != null && !minioConfig.getBucketName().isEmpty()) {
                    values.put("externalS3.bucketName", minioConfig.getBucketName());
                }
                if (minioConfig.getRootPath() != null && !minioConfig.getRootPath().isEmpty()) {
                    values.put("externalS3.rootPath", minioConfig.getRootPath());
                }
                
                log.info("ExternalS3 config - host: {}, port: {}, useSSL: {}, useIAM: {}, cloudProvider: {}, bucketName: {}, rootPath: {}",
                        minioConfig.getExternalEndpoints(), port, useSSL, minioConfig.isUseIAM(), cloudProvider, minioConfig.getBucketName(), minioConfig.getRootPath());
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

        // Woodpecker 配置（Milvus 2.6+ 流式存储组件，替代 Pulsar）
        WoodpeckerConfig woodpeckerConfig = params.getWoodpeckerConfig();
        boolean useWoodpecker = woodpeckerConfig != null && woodpeckerConfig.isEnabled();
        
        if (useWoodpecker) {
            // 启用 Woodpecker 模式
            log.info("Enabling Woodpecker mode (replacing Pulsar)");
            
            // 启用 streaming 模式
            values.put("streaming.enabled", "true");
            
            // 禁用 Pulsar 和 Kafka
            values.put("pulsar.enabled", "false");
            values.put("pulsarv3.enabled", "false");
            values.put("kafka.enabled", "false");
            
            // 禁用 indexNode（streaming 架构不需要）
            values.put("indexNode.enabled", "false");
            
            // 启用 Woodpecker
            values.put("woodpecker.enabled", "true");
            values.put("woodpecker.image.repository", "harbor.milvus.io/milvus/woodpecker");
            values.put("woodpecker.image.tag", "latest");

            // Woodpecker 存储类型配置
            String storageType = woodpeckerConfig.getStorageType();
            if (storageType == null || storageType.isEmpty()) {
                storageType = "minio"; // 默认使用 minio
            }
            
            if ("local".equalsIgnoreCase(storageType)) {
                // 本地存储模式
                values.put("streaming.woodpecker.storage.type", "local");
                log.info("Woodpecker storage type: local (requires shared filesystem for cluster mode)");
            } else if ("service".equalsIgnoreCase(storageType)) {
                // 独立服务模式
                values.put("streaming.woodpecker.embedded", "false");
                
                // Woodpecker 副本数
                int replicas = woodpeckerConfig.getReplicas();
                if (replicas > 0) {
                    values.put("woodpecker.replicaCount", String.valueOf(replicas));
                }
                
                // Woodpecker 资源配置
                if (woodpeckerConfig.getCpuRequest() != null && !woodpeckerConfig.getCpuRequest().isEmpty()) {
                    values.put("woodpecker.resources.requests.cpu", woodpeckerConfig.getCpuRequest());
                }
                if (woodpeckerConfig.getCpuLimit() != null && !woodpeckerConfig.getCpuLimit().isEmpty()) {
                    values.put("woodpecker.resources.limits.cpu", woodpeckerConfig.getCpuLimit());
                }
                if (woodpeckerConfig.getMemoryRequest() != null && !woodpeckerConfig.getMemoryRequest().isEmpty()) {
                    values.put("woodpecker.resources.requests.memory", woodpeckerConfig.getMemoryRequest());
                }
                if (woodpeckerConfig.getMemoryLimit() != null && !woodpeckerConfig.getMemoryLimit().isEmpty()) {
                    values.put("woodpecker.resources.limits.memory", woodpeckerConfig.getMemoryLimit());
                }
                
                log.info("Woodpecker storage type: service (standalone service mode), replicas: {}", replicas > 0 ? replicas : "default(4)");
            } else {
                // minio 模式（默认），不需要额外配置
                log.info("Woodpecker storage type: minio (default, recommended for production)");
            }
            
        } else if ("cluster".equalsIgnoreCase(milvusMode)) {
            // 传统消息队列配置（仅 Cluster 模式）
            // 使用 pulsarv3 替代旧版 pulsar
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

        // 自定义 Helm Values（Key-Value 列表）- 用于 K8s 资源层面的配置
        List<HelmConfigItem> customHelmValues = params.getCustomHelmValues();
        if (customHelmValues != null && !customHelmValues.isEmpty()) {
            for (HelmConfigItem item : customHelmValues) {
                if (item.getKey() != null && !item.getKey().isEmpty()) {
                    values.put(item.getKey(), item.getValue() != null ? item.getValue() : "");
                    log.info("Custom Helm value: " + item.getKey() + "=" + item.getValue());
                }
            }
        }

        // Milvus 运行时配置（Key-Value 列表）- 用于 Milvus 应用层面的配置
        // 会被转换为 extraConfigFiles.user.yaml 的内容
        List<HelmConfigItem> milvusConfigItems = params.getMilvusConfigItems();
        if (milvusConfigItems != null && !milvusConfigItems.isEmpty()) {
            // 将 Key-Value 列表转换为 YAML 格式
            String yamlContent = configItemsToYaml(milvusConfigItems);
            // 设置到 extraConfigFiles.user\.yaml（需要转义点号）
            values.put("extraConfigFiles.user\\.yaml", yamlContent);
            log.info("Milvus config (user.yaml):\n" + yamlContent);
        }

        return values;
    }

    /**
     * 将 Key-Value 配置列表转换为 YAML 格式字符串
     * <p>
     * 例如：
     * - key: "log.level", value: "debug" -> "log:\n  level: debug\n"
     * - key: "common.security.authorizationEnabled", value: "true" -> "common:\n  security:\n    authorizationEnabled: true\n"
     */
    private static String configItemsToYaml(List<HelmConfigItem> items) {
        // 构建树形结构
        Map<String, Object> tree = new LinkedHashMap<>();

        for (HelmConfigItem item : items) {
            if (item.getKey() == null || item.getKey().isEmpty()) {
                continue;
            }
            String[] parts = item.getKey().split("\\.");
            Map<String, Object> current = tree;

            for (int i = 0; i < parts.length - 1; i++) {
                current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
            }
            current.put(parts[parts.length - 1], item.getValue());
        }

        // 将树形结构转换为 YAML
        return treeToYaml(tree, 0);
    }

    /**
     * 将树形结构转换为 YAML 格式字符串
     */
    @SuppressWarnings("unchecked")
    private static String treeToYaml(Map<String, Object> tree, int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);

        for (Map.Entry<String, Object> entry : tree.entrySet()) {
            if (entry.getValue() instanceof Map) {
                sb.append(indentStr).append(entry.getKey()).append(":\n");
                sb.append(treeToYaml((Map<String, Object>) entry.getValue(), indent + 1));
            } else {
                sb.append(indentStr).append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
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
     * 构建失败结果
     */
    private static HelmCreateInstanceResult buildFailResult(String message, LocalDateTime startTime, String releaseName) {
        int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        return HelmCreateInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message(message)
                        .build())
                .releaseName(releaseName)
                .deploymentCostSeconds(costSeconds)
                .build();
    }

    /**
     * 根据 envEnum 获取对应的 cloudProvider
     * <p>
     * 映射关系：
     * - AWS_WEST -> aws
     * - GCP_WEST -> gcp
     * - AZURE_WEST -> azure
     * - ALI_HZ -> aliyun
     * - TC_NJ -> tencent
     * - HWC -> huaweicloud
     * - 其他 -> minio (默认)
     */
    private static String getCloudProviderFromEnv() {
        if (envEnum == null) {
            return "minio";
        }
        switch (envEnum) {
            case AWS_WEST:
                return "aws";
            case GCP_WEST:
                return "gcp";
            case AZURE_WEST:
                return "azure";
            case ALI_HZ:
                return "aliyun";
            case TC_NJ:
                return "tencent";
            case HWC:
                return "huaweicloud";
            default:
                return "minio";
        }
    }

    /**
     * 根据云平台获取默认端口
     * <p>
     * 端口规则：
     * - 公有云（aws/azure/gcp/aliyun/tencent/huaweicloud）：443
     * - 自建 MinIO：9000
     *
     * @param cloudProvider 云服务商
     * @return 端口号
     */
    private static int getDefaultPort(String cloudProvider) {
        if ("minio".equalsIgnoreCase(cloudProvider)) {
            return 9000;
        }
        return 443;
    }

    /**
     * 根据云平台获取默认 useSSL 配置
     * <p>
     * SSL 规则：
     * - 公有云（aws/azure/gcp/aliyun/tencent/huaweicloud）：true
     * - 自建 MinIO：false
     *
     * @param cloudProvider 云服务商
     * @return 是否使用 SSL
     */
    private static boolean getDefaultUseSSL(String cloudProvider) {
        if ("minio".equalsIgnoreCase(cloudProvider)) {
            return false;
        }
        return true;
    }
}
