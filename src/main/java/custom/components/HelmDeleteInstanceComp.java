package custom.components;

import custom.entity.HelmDeleteInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.HelmDeleteInstanceResult;
import custom.entity.result.ResultEnum;
import custom.utils.HelmUtils;
import custom.utils.KubernetesUtils;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static custom.BaseTest.*;
import static custom.BaseTest.envEnum;

/**
 * Helm 方式删除 Milvus 实例组件。
 * <p>
 * 通过 Helm 卸载 Milvus 实例，可选删除 PVC 和命名空间。
 */
@Slf4j
public class HelmDeleteInstanceComp {

    /**
     * 删除 Milvus 实例
     *
     * @param params 删除参数
     * @return 删除结果
     */
    public static HelmDeleteInstanceResult deleteInstance(HelmDeleteInstanceParams params) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("Starting Helm delete instance...");

        String releaseName = params.getReleaseName();
        String namespace = params.getNamespace();
        boolean pvcsDeleted = false;
        boolean namespaceDeleted = false;

        try {
            // 参数校验
            if (releaseName == null || releaseName.isEmpty()) {
                return buildFailResult("Release name is required", startTime, releaseName, false, false);
            }
            if (namespace == null || namespace.isEmpty()) {
                namespace = "qa";
            }

            // kubeconfig 路径由 EnvEnum 控制
            String kubeconfigPath = envEnum != null ? envEnum.kubeConfig : null;
            log.info("Using kubeconfig: " + kubeconfigPath);

            // 1. 检查 Release 是否存在
            log.info("Step 1: Checking if release exists...");
            if (!HelmUtils.releaseExists(releaseName, namespace, kubeconfigPath)) {
                log.warn("Release does not exist: " + releaseName);
                return buildFailResult("Release does not exist: " + releaseName, startTime, releaseName, false, false);
            }

            // 2. 卸载 Helm Release
            log.info("Step 2: Uninstalling Helm release...");
            HelmUtils.CommandResult uninstallResult = HelmUtils.uninstall(
                    releaseName,
                    namespace,
                    kubeconfigPath
            );

            if (!uninstallResult.isSuccess()) {
                log.error("Failed to uninstall release: " + uninstallResult.getStderr());
                return buildFailResult("Failed to uninstall release: " + uninstallResult.getStderr(),
                        startTime, releaseName, false, false);
            }
            log.info("Helm release uninstalled successfully");

            // 3. 初始化 K8s 客户端
            CoreV1Api coreApi = KubernetesUtils.createCoreV1Api(kubeconfigPath);

            // 4. 删除 PVC（如果需要）
            if (params.isDeletePvcs()) {
                log.info("Step 3: Deleting PVCs...");
                String labelSelector = "app.kubernetes.io/instance=" + releaseName;
                int deletedCount = KubernetesUtils.deletePvcs(coreApi, namespace, labelSelector);
                log.info("Deleted " + deletedCount + " PVCs");
                pvcsDeleted = deletedCount > 0 || deletedCount == 0; // true if operation completed
                pvcsDeleted = true;
            }

            // 5. 等待资源清理
            int waitTimeout = params.getWaitTimeoutMinutes();
            if (waitTimeout <= 0) {
                waitTimeout = 5;
            }
            log.info("Step 4: Waiting for resources to be cleaned up...");
            Thread.sleep(10 * 1000); // 等待 10 秒让 K8s 清理资源

            // 6. 删除命名空间（如果需要且命名空间为空）
            if (params.isDeleteNamespace()) {
                log.info("Step 5: Checking if namespace can be deleted...");
                // 等待一段时间确保资源已清理
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(waitTimeout);
                boolean isEmpty = false;

                while (LocalDateTime.now().isBefore(endTime)) {
                    isEmpty = KubernetesUtils.isNamespaceEmpty(coreApi, namespace);
                    if (isEmpty) {
                        break;
                    }
                    log.info("Namespace not empty yet, waiting...");
                    Thread.sleep(10 * 1000);
                }

                if (isEmpty) {
                    log.info("Namespace is empty, deleting...");
                    namespaceDeleted = KubernetesUtils.deleteNamespace(coreApi, namespace);
                    if (namespaceDeleted) {
                        log.info("Namespace deleted successfully: " + namespace);
                    } else {
                        log.warn("Failed to delete namespace: " + namespace);
                    }
                } else {
                    log.warn("Namespace is not empty, skipping deletion");
                }
            }

            // 7. 清理客户端状态
            if (milvusClientV2 != null) {
                try {
                    milvusClientV2.close();
                    milvusClientV2 = null;
                    log.info("Milvus clientV2 closed");
                } catch (Exception e) {
                    log.warn("Failed to close clientV2: " + e.getMessage());
                }
            }
            if (milvusClientV1 != null) {
                try {
                    milvusClientV1.close();
                    milvusClientV1 = null;
                    log.info("Milvus clientV1 closed");
                } catch (Exception e) {
                    log.warn("Failed to close clientV1: " + e.getMessage());
                }
            }

            // 8. 构建成功结果
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            log.info("Milvus instance deleted successfully, cost: " + costSeconds + " seconds");

            return HelmDeleteInstanceResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message("Milvus instance deleted successfully")
                            .build())
                    .releaseName(releaseName)
                    .costSeconds(costSeconds)
                    .pvcsDeleted(pvcsDeleted)
                    .namespaceDeleted(namespaceDeleted)
                    .build();

        } catch (Exception e) {
            log.error("Failed to delete Milvus instance", e);
            return buildFailResult("Exception: " + e.getMessage(), startTime, releaseName, pvcsDeleted, namespaceDeleted);
        }
    }

    /**
     * 构建失败结果
     */
    private static HelmDeleteInstanceResult buildFailResult(
            String message,
            LocalDateTime startTime,
            String releaseName,
            boolean pvcsDeleted,
            boolean namespaceDeleted) {

        int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        return HelmDeleteInstanceResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.EXCEPTION.result)
                        .message(message)
                        .build())
                .releaseName(releaseName != null ? releaseName : "")
                .costSeconds(costSeconds)
                .pvcsDeleted(pvcsDeleted)
                .namespaceDeleted(namespaceDeleted)
                .build();
    }
}
