package custom.entity.result;

import lombok.Builder;
import lombok.Data;

/**
 * Helm 方式删除 Milvus 实例的结果。
 */
@Data
@Builder
public class HelmDeleteInstanceResult {

    /**
     * 通用结果（成功/失败/警告 + 消息）。
     */
    CommonResult commonResult;

    /**
     * 已删除的 Helm Release 名称。
     */
    String releaseName;

    /**
     * 删除操作耗时（秒）。
     */
    int costSeconds;

    /**
     * 是否成功删除 PVC。
     */
    boolean pvcsDeleted;

    /**
     * 是否成功删除命名空间。
     */
    boolean namespaceDeleted;
}
