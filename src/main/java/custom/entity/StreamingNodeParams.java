package custom.entity;

import lombok.Data;

/**
 * Streaming Node 的临时配置（创建实例时可选）。
 * <p>
 * 对应前端组件：`createInstanceEdit.vue` -> "Modify streaming node config"
 */
@Data
public class StreamingNodeParams {
    /**
     * streamingNode replica 数。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Replica"
     * <p>
     * 前端默认值：""（空字符串，占位）
     * <p>
     * 注意：后端字段类型为 int，建议生成 JSON 时传数字（例如 0/1/2）或不传。
     */
    int replicaNum;

    /**
     * CPU 配置（字符串，按平台约定填写）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "CPU"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String cpu;

    /**
     * Memory 配置（字符串，前端提示需带单位 Gi，例如 "4Gi"）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Memory (需添加单位Gi)"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String memory;

    /**
     * Disk 配置（字符串，前端提示需带单位 Gi，例如 "64Gi"）。
     * <p>
     * 前端：`createInstanceEdit.vue` -> "Disk (需添加单位Gi)"
     * <p>
     * 前端默认值：""（空字符串）
     */
    String disk;
}
