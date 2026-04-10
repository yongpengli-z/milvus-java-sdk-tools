package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * UpdateInstanceComponent 参数（一次调用批量修改一个实例中多个 NodeCategory 的 replicas / requests / limits，
 * 修改完成后自动触发 restart 让配置生效）。
 * <p>
 * 对应 RM 接口：
 * <ul>
 *     <li>POST /resource/v1/instance/milvus/update_replicas</li>
 *     <li>POST /resource/v1/instance/milvus/update_requests</li>
 *     <li>POST /resource/v1/instance/milvus/update_limits</li>
 *     <li>POST /resource/v1/instance/milvus/restart</li>
 * </ul>
 * <p>
 * 实现要点：
 * <ol>
 *   <li>RM 每个 update 接口的 NodeCategories 是广播语义（列表里所有 category 共享同一组值），
 *       因此本组件在 tool 层按 NodeCategory 拆开，每个 spec 独立发起 1-3 次 RM 调用。</li>
 *   <li>RM 的 update_requests / update_limits 会整组覆盖 ResourceList，所以当 spec 只指定了
 *       cpu 或 memory 其中一个时，组件会先 describe 当前值，把另一个回填进 payload 避免被清空。</li>
 *   <li>任何一次 RM update 失败都会立即返回 WARNING，不执行后续也不 restart。</li>
 *   <li>全部 update 成功后复用 RestartInstanceComp 等待实例回到 RUNNING。</li>
 * </ol>
 */
@Data
public class UpdateInstanceComponentParams {

    /**
     * 实例 ID，例 in01-4e42a675542d262。
     * 为空时回退到 BaseTest.newInstanceInfo.getInstanceId()。
     */
    String instanceId;

    String accountEmail;
    String accountPassword;

    /**
     * 每个 NodeCategory 的目标规格。同一个 (category, replicaIndex) 组合建议不要重复出现，
     * 如果出现，按 list 顺序依次执行，后者会覆盖前者。
     */
    List<ComponentSpec> specs;

    @Data
    public static class ComponentSpec {
        /**
         * NodeCategory 的 specName（小写驼峰），例如：
         * queryNode / dataNode / indexNode / standalone / proxy /
         * mixCoord / streamingNode / rootCoord / queryCoord / dataCoord / indexCoord
         */
        String category;

        /**
         * 多副本组场景指定组下标。传 null 时由 tool 层先 describe 一次，把该 category 下
         * 所有真实存在的 replicaIndex 展开，逐个下发 RM。
         * <p>
         * 注意：不能指望 RM 端自己处理 null —— RM 的 update_replicas / update_requests /
         * update_limits 在 DAO 层直接用 `replica_index = NULL` 等值查询，SQL 永远匹配不上，
         * 接口会"静默 no-op 并返回 Code=0"。这是 RM 的历史坑，tool 层必须自己展开。
         * <p>
         * 单副本组场景直接传 null 即可。
         */
        Integer replicaIndex;

        /** 目标 pod 副本数。null 表示不修改 replicas。 */
        Integer replicas;

        /**
         * 目标 CPU request（k8s Quantity 字符串，必须带单位，工具不会补默认单位）。
         * 合法例子："8000m"、"500m"、"2"（纯数字等价于 cores）。
         * null 表示不修改。原样透传给 RM。
         */
        String cpuRequest;

        /**
         * 目标 Memory request（k8s Quantity 字符串，必须带单位）。
         * 合法例子："32Gi"、"8192Mi"。null 表示不修改。原样透传给 RM。
         */
        String memoryRequest;

        /** 目标 CPU limit（格式同 cpuRequest）。null 表示不修改。 */
        String cpuLimit;

        /** 目标 Memory limit（格式同 memoryRequest）。null 表示不修改。 */
        String memoryLimit;
    }
}
