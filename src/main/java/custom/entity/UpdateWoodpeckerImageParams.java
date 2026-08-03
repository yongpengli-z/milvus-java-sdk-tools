package custom.entity;

import lombok.Data;

/**
 * Update Woodpecker Image 参数（通过 Cloud Ops 提交 Woodpecker upgrade workflow）。
 */
@Data
public class UpdateWoodpeckerImageParams {
    /**
     * Region ID（可选；为空时使用当前环境配置）。
     */
    String regionId;

    /**
     * Woodpecker 集群 ID。
     */
    String woodpeckerId;

    /**
     * 目标 Woodpecker image tag。
     */
    String newImageTag;
}
