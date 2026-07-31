package custom.entity;

import lombok.Data;

/**
 * Switch Instance MQ 参数（通过 Cloud Ops 提交实例 MQ 切换 workflow）。
 */
@Data
public class SwitchInstanceMqParams {
    /**
     * 实例 ID（可选；为空时使用当前任务实例）。
     */
    String instanceId;

    /**
     * Region ID（可选；为空时使用当前环境配置）。
     */
    String regionId;

    /**
     * 目标 MQ 类型：woodpecker / kafka / pulsar。
     */
    String targetMqType;

    /**
     * 目标 Woodpecker 集群 ID。
     * <p>
     * 当 targetMqType=woodpecker 时必填；切回 kafka/pulsar 时不需要。
     */
    String targetWoodpeckerId;
}
