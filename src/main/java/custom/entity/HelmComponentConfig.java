package custom.entity;

import lombok.Data;

/**
 * Helm 组件配置（用于 Cluster 模式下各组件的独立配置）。
 * <p>
 * 支持配置副本数和资源限制。
 */
@Data
public class HelmComponentConfig {

    /**
     * 副本数。
     * <p>
     * 示例值：1、2、3
     * <p>
     * 前端默认值：1
     */
    int replicas;

    /**
     * CPU Request。
     * <p>
     * 示例值：`100m`、`500m`、`1`、`2`
     */
    String cpuRequest;

    /**
     * CPU Limit。
     * <p>
     * 示例值：`2`、`4`、`8`
     */
    String cpuLimit;

    /**
     * Memory Request。
     * <p>
     * 示例值：`512Mi`、`1Gi`、`2Gi`
     */
    String memoryRequest;

    /**
     * Memory Limit。
     * <p>
     * 示例值：`2Gi`、`4Gi`、`8Gi`
     */
    String memoryLimit;

    /**
     * 磁盘大小（仅适用于某些组件，如 queryNode 的磁盘索引）。
     * <p>
     * 示例值：`50Gi`、`100Gi`
     */
    String diskSize;
}
