package custom.entity;

import lombok.Data;

/**
 * Woodpecker 配置（Milvus 2.6+ 流式存储组件）。
 * <p>
 * Woodpecker 是 Milvus 2.6+ 中替代 Pulsar 的新流式存储组件，用于 WAL（Write-Ahead Log）管理。
 * <p>
 * <h2>存储类型说明</h2>
 * <ul>
 *   <li><b>minio</b>（默认）：使用 MinIO 对象存储，适用于多节点环境，<b>生产推荐</b></li>
 *   <li><b>local</b>：本地存储，仅适用于单节点或具备共享文件系统(NFS/CephFS)的环境</li>
 *   <li><b>service</b>：Woodpecker 作为独立服务模式，外置于 streaming node</li>
 * </ul>
 * <p>
 * <h2>模式支持矩阵</h2>
 * <table border="1">
 *   <tr><th>模式</th><th>minio</th><th>local</th><th>service</th></tr>
 *   <tr><td>Standalone</td><td>✅</td><td>✅</td><td>✅</td></tr>
 *   <tr><td>Cluster</td><td>✅</td><td>⚠️需共享存储</td><td>✅</td></tr>
 * </table>
 * <p>
 * <h2>JSON 示例</h2>
 * <p>
 * <b>minio 模式（推荐）：</b>
 * <pre>
 * {
 *   "enabled": true,
 *   "storageType": "minio"
 * }
 * </pre>
 * <p>
 * <b>service 模式（支持配置 replicas 和 resources）：</b>
 * <pre>
 * {
 *   "enabled": true,
 *   "storageType": "service",
 *   "replicas": 4,
 *   "cpuRequest": "500m",
 *   "cpuLimit": "2",
 *   "memoryRequest": "512Mi",
 *   "memoryLimit": "2Gi"
 * }
 * </pre>
 * <p>
 * <b>注意：</b>启用 Woodpecker 后会自动：
 * <ul>
 *   <li>启用 streaming 模式（streaming.enabled=true）</li>
 *   <li>禁用 Pulsar（pulsarv3.enabled=false）</li>
 *   <li>禁用 indexNode（indexNode.enabled=false）</li>
 * </ul>
 */
@Data
public class WoodpeckerConfig {

    /**
     * 是否启用 Woodpecker。
     * <p>
     * true：启用 Woodpecker 替代 Pulsar
     * false：使用传统的 Pulsar/Kafka
     * <p>
     * 前端默认值：false
     */
    boolean enabled;

    /**
     * Woodpecker 存储类型。
     * <p>
     * 可选值：
     * <ul>
     *   <li><b>minio</b>（默认）：使用 MinIO 对象存储，生产推荐</li>
     *   <li><b>local</b>：本地存储，Cluster 模式需共享文件系统</li>
     *   <li><b>service</b>：独立服务模式，支持配置 replicas 和 resources</li>
     * </ul>
     * <p>
     * 前端默认值：minio
     */
    String storageType;

    // ==================== Service 模式配置（storageType=service 时生效）====================

    /**
     * Woodpecker 副本数（仅 service 模式生效）。
     * <p>
     * 前端默认值：4
     */
    int replicas;

    /**
     * CPU 请求（仅 service 模式生效）。
     * <p>
     * 示例值：500m, 1, 2
     * <p>
     * 前端默认值：500m
     */
    String cpuRequest;

    /**
     * CPU 限制（仅 service 模式生效）。
     * <p>
     * 示例值：1, 2, 4
     * <p>
     * 前端默认值：1
     */
    String cpuLimit;

    /**
     * 内存请求（仅 service 模式生效）。
     * <p>
     * 示例值：512Mi, 1Gi, 2Gi
     * <p>
     * 前端默认值：512Mi
     */
    String memoryRequest;

    /**
     * 内存限制（仅 service 模式生效）。
     * <p>
     * 示例值：1Gi, 2Gi, 4Gi
     * <p>
     * 前端默认值：1Gi
     */
    String memoryLimit;
}
