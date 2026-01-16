package custom.entity;

import lombok.Data;

/**
 * Helm 配置项（Key-Value 格式）。
 * <p>
 * 用于存储单个 Helm --set 参数。
 */
@Data
public class HelmConfigItem {

    /**
     * 配置键（dot notation 格式）。
     * <p>
     * 示例：
     * - `proxy.replicas`
     * - `service.type`
     * - `image.tag`
     * - `standalone.resources.limits.memory`
     */
    String key;

    /**
     * 配置值。
     * <p>
     * 示例：
     * - `2`
     * - `NodePort`
     * - `v2.5.0`
     * - `8Gi`
     */
    String value;
}
