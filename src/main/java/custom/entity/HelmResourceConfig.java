package custom.entity;

import lombok.Data;

/**
 * Helm 资源配置（CPU/Memory Requests 和 Limits）。
 * <p>
 * 用于配置 Milvus 组件的资源请求和限制。
 */
@Data
public class HelmResourceConfig {

    /**
     * CPU Request。
     * <p>
     * 示例值：`100m`、`500m`、`1`、`2`
     * <p>
     * 前端默认值：`100m`
     */
    String cpuRequest;

    /**
     * CPU Limit。
     * <p>
     * 示例值：`2`、`4`、`8`
     * <p>
     * 前端默认值：`2`
     */
    String cpuLimit;

    /**
     * Memory Request。
     * <p>
     * 示例值：`512Mi`、`1Gi`、`2Gi`
     * <p>
     * 前端默认值：`512Mi`
     */
    String memoryRequest;

    /**
     * Memory Limit。
     * <p>
     * 示例值：`2Gi`、`4Gi`、`8Gi`
     * <p>
     * 前端默认值：`2Gi`
     */
    String memoryLimit;
}
