package custom.entity.result;

import custom.utils.KubernetesUtils;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Helm 方式创建 Milvus 实例的结果。
 */
@Data
@Builder
public class HelmCreateInstanceResult {

    /**
     * 通用结果（成功/失败/警告 + 消息）。
     */
    CommonResult commonResult;

    /**
     * Milvus 连接 URI。
     * <p>
     * 格式：`http://<loadBalancerIp>:19530`
     */
    String uri;

    /**
     * Helm Release 名称。
     */
    String releaseName;

    /**
     * Kubernetes 命名空间。
     */
    String namespace;

    /**
     * 部署模式（standalone/cluster）。
     */
    String milvusMode;

    /**
     * 实际部署的 Milvus 镜像版本。
     */
    String milvusVersion;

    /**
     * 部署耗时（秒）。
     */
    int deploymentCostSeconds;

    /**
     * Pod 状态信息列表。
     */
    List<KubernetesUtils.PodStatus> podStatus;

    /**
     * 创建时间。
     */
    LocalDateTime createTime;

    /**
     * 预计使用时长（小时）。
     * <p>
     * 0 表示不限制。
     */
    int useHours;

    /**
     * 预计到期时间。
     * <p>
     * 如果 useHours 为 0，则该字段为 null（表示永久）。
     */
    LocalDateTime expireTime;
}
