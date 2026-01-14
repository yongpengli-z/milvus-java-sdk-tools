package custom.entity;

import lombok.Data;

/**
 * Helm 方式删除 Milvus 实例参数。
 * <p>
 * 用于卸载通过 Helm 部署的 Milvus 实例，支持清理 PVC 和命名空间。
 */
@Data
public class HelmDeleteInstanceParams {
    // 注意：kubeconfig 路径由 EnvEnum 内置控制，不需要在参数中指定
    // 注意：命名空间已预先创建好，默认使用 qa

    /**
     * Kubernetes 命名空间。
     * <p>
     * 前端默认值：`qa`
     */
    String namespace;

    /**
     * Helm Release 名称。
     * <p>
     * 前端必填：是
     * <p>
     * 前端默认值：""
     */
    String releaseName;

    /**
     * 是否删除 PVC（持久化存储卷）。
     * <p>
     * true：同时删除关联的 PVC，彻底清理数据
     * false：保留 PVC，数据可恢复
     * <p>
     * 前端默认值：false
     */
    boolean deletePvcs;

    /**
     * 是否删除命名空间。
     * <p>
     * 仅在命名空间为空（没有其他资源）时才会删除。
     * <p>
     * 前端默认值：false
     */
    boolean deleteNamespace;

    /**
     * 等待资源清理完成的超时时间（分钟）。
     * <p>
     * 前端默认值：10
     */
    int waitTimeoutMinutes;
}
