package custom.utils;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.FileReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Kubernetes 操作工具类。
 * <p>
 * 使用官方 Kubernetes Java Client 进行操作。
 */
@Slf4j
public class KubernetesUtils {

    /**
     * 创建 API Client
     *
     * @param kubeconfigPath kubeconfig 文件路径（为空则使用默认）
     * @return ApiClient 实例
     */
    public static ApiClient createApiClient(String kubeconfigPath) throws Exception {
        ApiClient client;
        if (kubeconfigPath == null || kubeconfigPath.isEmpty()) {
            // 使用默认 kubeconfig (~/.kube/config)
            log.info("Using default kubeconfig");
            client = Config.defaultClient();
        } else {
            // 使用指定 kubeconfig
            log.info("Using kubeconfig: " + kubeconfigPath);
            KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new FileReader(kubeconfigPath));
            client = Config.fromConfig(kubeConfig);
        }
        Configuration.setDefaultApiClient(client);
        return client;
    }

    /**
     * 创建 CoreV1Api 实例
     *
     * @param kubeconfigPath kubeconfig 文件路径
     * @return CoreV1Api 实例
     */
    public static CoreV1Api createCoreV1Api(String kubeconfigPath) throws Exception {
        createApiClient(kubeconfigPath);
        return new CoreV1Api();
    }

    /**
     * 创建命名空间（如果不存在）
     *
     * @param coreApi   CoreV1Api 实例
     * @param namespace 命名空间名称
     * @return 是否创建成功（已存在也返回 true）
     */
    public static boolean createNamespaceIfNotExists(CoreV1Api coreApi, String namespace) {
        try {
            // 检查命名空间是否存在
            coreApi.readNamespace(namespace, null);
            log.info("Namespace already exists: " + namespace);
            return true;
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                // 不存在，创建
                try {
                    V1Namespace ns = new V1Namespace()
                            .metadata(new V1ObjectMeta().name(namespace));
                    coreApi.createNamespace(ns, null, null, null, null);
                    log.info("Namespace created: " + namespace);
                    return true;
                } catch (ApiException createEx) {
                    log.error("Failed to create namespace: " + namespace, createEx);
                    return false;
                }
            } else {
                log.error("Failed to check namespace: " + namespace, e);
                return false;
            }
        }
    }

    /**
     * 等待所有 Pod Ready
     *
     * @param coreApi        CoreV1Api 实例
     * @param namespace      命名空间
     * @param labelSelector  标签选择器（如 app.kubernetes.io/instance=my-milvus）
     * @param timeoutMinutes 超时时间（分钟）
     * @return Pod 状态列表
     */
    public static List<PodStatus> waitForPodsReady(
            CoreV1Api coreApi,
            String namespace,
            String labelSelector,
            int timeoutMinutes) {

        List<PodStatus> podStatuses = new ArrayList<>();
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(timeoutMinutes);

        log.info("Waiting for pods to be ready in namespace: " + namespace + ", selector: " + labelSelector);

        while (LocalDateTime.now().isBefore(endTime)) {
            try {
                V1PodList podList = coreApi.listNamespacedPod(
                        namespace,
                        null,  // pretty
                        null,  // allowWatchBookmarks
                        null,  // _continue
                        null,  // fieldSelector
                        labelSelector,
                        null,  // limit
                        null,  // resourceVersion
                        null,  // resourceVersionMatch
                        null,  // timeoutSeconds
                        null   // watch
                );

                podStatuses.clear();
                boolean allReady = true;
                int totalPods = podList.getItems().size();
                int readyPods = 0;

                for (V1Pod pod : podList.getItems()) {
                    String podName = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown";
                    String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
                    boolean isReady = isPodReady(pod);

                    if (isReady) {
                        readyPods++;
                    } else {
                        allReady = false;
                    }

                    String message = "";
                    if (pod.getStatus() != null && pod.getStatus().getConditions() != null) {
                        for (V1PodCondition condition : pod.getStatus().getConditions()) {
                            if ("Ready".equals(condition.getType()) && !"True".equals(condition.getStatus())) {
                                message = condition.getMessage() != null ? condition.getMessage() : "";
                                break;
                            }
                        }
                    }

                    podStatuses.add(PodStatus.builder()
                            .name(podName)
                            .phase(phase)
                            .ready(isReady)
                            .message(message)
                            .build());
                }

                log.info("Pod status: " + readyPods + "/" + totalPods + " ready");

                if (totalPods > 0 && allReady) {
                    log.info("All pods are ready!");
                    return podStatuses;
                }

                // 等待 10 秒再检查
                Thread.sleep(10 * 1000);

            } catch (ApiException e) {
                log.error("Failed to list pods", e);
            } catch (InterruptedException e) {
                log.warn("Wait interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.warn("Timeout waiting for pods to be ready");
        return podStatuses;
    }

    /**
     * 检查 Pod 是否 Ready
     */
    private static boolean isPodReady(V1Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }

        for (V1PodCondition condition : pod.getStatus().getConditions()) {
            if ("Ready".equals(condition.getType())) {
                return "True".equals(condition.getStatus());
            }
        }
        return false;
    }

    /**
     * 获取 Service NodePort
     *
     * @param coreApi     CoreV1Api 实例
     * @param namespace   命名空间
     * @param serviceName Service 名称
     * @return NodePort 端口号（未找到返回 -1）
     */
    public static int getServiceNodePort(
            CoreV1Api coreApi,
            String namespace,
            String serviceName) {

        try {
            V1Service service = coreApi.readNamespacedService(serviceName, namespace, null);

            if (service.getSpec() != null && service.getSpec().getPorts() != null) {
                for (V1ServicePort port : service.getSpec().getPorts()) {
                    // 查找 Milvus gRPC 端口 (19530)
                    if (port.getPort() != null && port.getPort() == 19530) {
                        Integer nodePort = port.getNodePort();
                        if (nodePort != null) {
                            log.info("Found NodePort for " + serviceName + ": " + nodePort);
                            return nodePort;
                        }
                    }
                }
                // 如果没找到 19530，返回第一个 NodePort
                for (V1ServicePort port : service.getSpec().getPorts()) {
                    if (port.getNodePort() != null) {
                        log.info("Using first available NodePort for " + serviceName + ": " + port.getNodePort());
                        return port.getNodePort();
                    }
                }
            }

            log.warn("No NodePort found for service: " + serviceName);
            return -1;

        } catch (ApiException e) {
            log.error("Failed to get service: " + serviceName, e);
            return -1;
        }
    }

    /**
     * 获取 Service LoadBalancer IP
     *
     * @param coreApi        CoreV1Api 实例
     * @param namespace      命名空间
     * @param serviceName    Service 名称
     * @param timeoutMinutes 超时时间
     * @return LoadBalancer IP（未获取到返回空字符串）
     */
    public static String getLoadBalancerIp(
            CoreV1Api coreApi,
            String namespace,
            String serviceName,
            int timeoutMinutes) {

        LocalDateTime endTime = LocalDateTime.now().plusMinutes(timeoutMinutes);

        log.info("Waiting for LoadBalancer IP for service: " + serviceName);

        while (LocalDateTime.now().isBefore(endTime)) {
            try {
                V1Service service = coreApi.readNamespacedService(serviceName, namespace, null);

                if (service.getStatus() != null &&
                        service.getStatus().getLoadBalancer() != null &&
                        service.getStatus().getLoadBalancer().getIngress() != null &&
                        !service.getStatus().getLoadBalancer().getIngress().isEmpty()) {

                    V1LoadBalancerIngress ingress = service.getStatus().getLoadBalancer().getIngress().get(0);
                    String ip = ingress.getIp();
                    if (ip != null && !ip.isEmpty()) {
                        log.info("LoadBalancer IP: " + ip);
                        return ip;
                    }
                    String hostname = ingress.getHostname();
                    if (hostname != null && !hostname.isEmpty()) {
                        log.info("LoadBalancer hostname: " + hostname);
                        return hostname;
                    }
                }

                log.info("Waiting for LoadBalancer IP...");
                Thread.sleep(10 * 1000);

            } catch (ApiException e) {
                log.error("Failed to get service: " + serviceName, e);
            } catch (InterruptedException e) {
                log.warn("Wait interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.warn("Timeout waiting for LoadBalancer IP");
        return "";
    }

    /**
     * 获取集群任意可用节点的 IP
     *
     * @param coreApi CoreV1Api 实例
     * @return 节点 IP（未找到返回空字符串）
     */
    public static String getAnyNodeIp(CoreV1Api coreApi) {
        try {
            V1NodeList nodeList = coreApi.listNode(
                    null,  // pretty
                    null,  // allowWatchBookmarks
                    null,  // _continue
                    null,  // fieldSelector
                    null,  // labelSelector
                    null,  // limit
                    null,  // resourceVersion
                    null,  // resourceVersionMatch
                    null,  // timeoutSeconds
                    null   // watch
            );

            for (V1Node node : nodeList.getItems()) {
                // 检查节点是否 Ready
                boolean isReady = false;
                if (node.getStatus() != null && node.getStatus().getConditions() != null) {
                    for (V1NodeCondition condition : node.getStatus().getConditions()) {
                        if ("Ready".equals(condition.getType()) && "True".equals(condition.getStatus())) {
                            isReady = true;
                            break;
                        }
                    }
                }

                if (!isReady) {
                    continue;
                }

                // 获取节点 IP
                if (node.getStatus() != null && node.getStatus().getAddresses() != null) {
                    // 优先使用 InternalIP
                    for (V1NodeAddress address : node.getStatus().getAddresses()) {
                        if ("InternalIP".equals(address.getType())) {
                            log.info("Found node InternalIP: " + address.getAddress());
                            return address.getAddress();
                        }
                    }
                    // 其次使用 ExternalIP
                    for (V1NodeAddress address : node.getStatus().getAddresses()) {
                        if ("ExternalIP".equals(address.getType())) {
                            log.info("Found node ExternalIP: " + address.getAddress());
                            return address.getAddress();
                        }
                    }
                }
            }

            log.warn("No node IP found");
            return "";

        } catch (ApiException e) {
            log.error("Failed to list nodes", e);
            return "";
        }
    }

    /**
     * 删除命名空间下所有 PVC
     *
     * @param coreApi       CoreV1Api 实例
     * @param namespace     命名空间
     * @param labelSelector 标签选择器（可选）
     * @return 删除的 PVC 数量
     */
    public static int deletePvcs(
            CoreV1Api coreApi,
            String namespace,
            String labelSelector) {

        int deletedCount = 0;

        try {
            V1PersistentVolumeClaimList pvcList = coreApi.listNamespacedPersistentVolumeClaim(
                    namespace,
                    null,  // pretty
                    null,  // allowWatchBookmarks
                    null,  // _continue
                    null,  // fieldSelector
                    labelSelector,
                    null,  // limit
                    null,  // resourceVersion
                    null,  // resourceVersionMatch
                    null,  // timeoutSeconds
                    null   // watch
            );

            for (V1PersistentVolumeClaim pvc : pvcList.getItems()) {
                String pvcName = pvc.getMetadata() != null ? pvc.getMetadata().getName() : null;
                if (pvcName == null) {
                    continue;
                }
                try {
                    coreApi.deleteNamespacedPersistentVolumeClaim(
                            pvcName,
                            namespace,
                            null,  // pretty
                            null,  // dryRun
                            null,  // gracePeriodSeconds
                            null,  // orphanDependents
                            null,  // propagationPolicy
                            null   // body
                    );
                    log.info("Deleted PVC: " + pvcName);
                    deletedCount++;
                } catch (ApiException e) {
                    log.error("Failed to delete PVC: " + pvcName, e);
                }
            }

        } catch (ApiException e) {
            log.error("Failed to list PVCs", e);
        }

        return deletedCount;
    }

    /**
     * 删除命名空间
     *
     * @param coreApi   CoreV1Api 实例
     * @param namespace 命名空间
     * @return 是否成功
     */
    public static boolean deleteNamespace(CoreV1Api coreApi, String namespace) {
        try {
            coreApi.deleteNamespace(
                    namespace,
                    null,  // pretty
                    null,  // dryRun
                    null,  // gracePeriodSeconds
                    null,  // orphanDependents
                    null,  // propagationPolicy
                    null   // body
            );
            log.info("Namespace deleted: " + namespace);
            return true;
        } catch (ApiException e) {
            log.error("Failed to delete namespace: " + namespace, e);
            return false;
        }
    }

    /**
     * 检查命名空间是否为空
     *
     * @param coreApi   CoreV1Api 实例
     * @param namespace 命名空间
     * @return 是否为空
     */
    public static boolean isNamespaceEmpty(CoreV1Api coreApi, String namespace) {
        try {
            // 检查 Pod
            V1PodList podList = coreApi.listNamespacedPod(
                    namespace,
                    null, null, null, null, null, null, null, null, null, null
            );
            if (!podList.getItems().isEmpty()) {
                log.info("Namespace has pods: " + podList.getItems().size());
                return false;
            }

            // 检查 Service
            V1ServiceList serviceList = coreApi.listNamespacedService(
                    namespace,
                    null, null, null, null, null, null, null, null, null, null
            );
            // 排除默认的 kubernetes service
            long serviceCount = serviceList.getItems().stream()
                    .filter(s -> s.getMetadata() != null && !"kubernetes".equals(s.getMetadata().getName()))
                    .count();
            if (serviceCount > 0) {
                log.info("Namespace has services: " + serviceCount);
                return false;
            }

            // 检查 PVC
            V1PersistentVolumeClaimList pvcList = coreApi.listNamespacedPersistentVolumeClaim(
                    namespace,
                    null, null, null, null, null, null, null, null, null, null
            );
            if (!pvcList.getItems().isEmpty()) {
                log.info("Namespace has PVCs: " + pvcList.getItems().size());
                return false;
            }

            return true;

        } catch (ApiException e) {
            log.error("Failed to check namespace", e);
            return false;
        }
    }

    /**
     * Pod 状态封装
     */
    @Data
    @Builder
    public static class PodStatus {
        String name;
        String phase;
        boolean ready;
        String message;
    }
}
