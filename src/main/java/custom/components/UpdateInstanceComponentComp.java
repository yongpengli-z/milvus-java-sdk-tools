package custom.components;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.entity.RestartInstanceParams;
import custom.entity.UpdateInstanceComponentParams;
import custom.entity.UpdateInstanceComponentParams.ComponentSpec;
import custom.entity.result.CommonResult;
import custom.entity.result.RestartInstanceResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.UpdateInstanceComponentResult;
import custom.entity.result.UpdateInstanceComponentResult.ChangeRecord;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

/**
 * 一次调用批量修改一个实例中多个 NodeCategory 的 replicas / requests / limits，
 * 修改完成后自动 restart 让配置真正生效。
 * <p>
 * 详细说明见 {@link UpdateInstanceComponentParams} 类注释。
 */
@Slf4j
public class UpdateInstanceComponentComp {

    public static UpdateInstanceComponentResult updateInstanceComponent(UpdateInstanceComponentParams params) {
        long startTs = System.currentTimeMillis();

        // 1) 账号登录（与其它 Comp 保持一致）
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().isEmpty()) {
            if (params.getAccountEmail() == null || params.getAccountEmail().isEmpty()) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            } else {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(
                        params.getAccountEmail(), params.getAccountPassword());
            }
        }

        List<ChangeRecord> changes = new ArrayList<>();

        // 2) instanceId 回退：留空时使用当前已创建的实例（与 ScaleInstanceComp 等保持一致）
        String instanceId = (params.getInstanceId() == null || params.getInstanceId().isEmpty())
                ? (newInstanceInfo == null ? null : newInstanceInfo.getInstanceId())
                : params.getInstanceId();
        params.setInstanceId(instanceId);

        // 2.1) 前端传过来的空串当作 null 处理，避免当成真实 Quantity 下发给 RM。
        if (params.getSpecs() != null) {
            for (ComponentSpec s : params.getSpecs()) {
                if (s == null) continue;
                s.setCpuRequest(nullIfBlank(s.getCpuRequest()));
                s.setMemoryRequest(nullIfBlank(s.getMemoryRequest()));
                s.setCpuLimit(nullIfBlank(s.getCpuLimit()));
                s.setMemoryLimit(nullIfBlank(s.getMemoryLimit()));
            }
        }

        // 3) 参数校验
        String validateMsg = validate(params);
        if (validateMsg != null) {
            return fail(changes, validateMsg, startTs);
        }

        // 3) describe 一次，仅用于补齐用户未指定的 cpu / memory 维度
        //    ShowAllNodes=true 保证多副本组的节点都能拿到
        Map<NodeKey, JSONObject> nodeMap;
        try {
            nodeMap = loadNodeMap(instanceId);
        } catch (Exception e) {
            log.error("describe instance failed", e);
            return fail(changes, "describe instance failed: " + e.getMessage(), startTs);
        }

        // 4) 按 spec 顺序拆分成若干 RM 调用，任一失败立即返回（快速失败）
        //    注意：RM 的 update_replicas / update_requests / update_limits 在 DAO 层用
        //    `replica_index = NULL` 这种等值查询，replicaIndex 传 null 时 SQL 永远匹配不上
        //    任何行，接口会"静默无操作"并返回 Code=0。因此 tool 层必须自己把 null 展开成
        //    该 category 下所有真实的 replicaIndex 逐个下发。
        for (ComponentSpec spec : params.getSpecs()) {
            List<String> categories = Collections.singletonList(spec.getCategory());
            List<Integer> targetIndexes = resolveReplicaIndexes(spec, nodeMap);
            if (targetIndexes.isEmpty()) {
                return fail(changes,
                        "no node found for category=" + spec.getCategory()
                                + (spec.getReplicaIndex() == null
                                        ? "" : (" replicaIndex=" + spec.getReplicaIndex())),
                        startTs);
            }

            for (Integer idx : targetIndexes) {
                // 4.1 replicas
                if (spec.getReplicas() != null) {
                    String payload = String.format("{\"replicas\":%d,\"replicaIndex\":%s}",
                            spec.getReplicas(), String.valueOf(idx));
                    try {
                        String resp = ResourceManagerServiceUtils.updateReplicas(
                                instanceId, spec.getReplicas(), categories, idx);
                        ChangeRecord cr = recordFromRmResp(spec, idx, "replicas", payload, resp);
                        changes.add(cr);
                        if (!cr.isSuccess()) {
                            return fail(changes, "update_replicas failed: " + cr.getMessage(), startTs);
                        }
                    } catch (Exception e) {
                        log.error("update_replicas threw", e);
                        changes.add(exceptionRecord(spec, idx, "replicas", payload, e));
                        return fail(changes, "update_replicas threw: " + e.getMessage(), startTs);
                    }
                }

                // 4.2 requests（无论用户是否传值都会下发：全量回填 + 必要时合并用户值，
                //      配合后续 restart 确保配置被真正推到 pod）
                {
                    String payload = null;
                    try {
                        Map<String, String> requestList = buildResourceList(
                                spec.getCpuRequest(), spec.getMemoryRequest(),
                                nodeMap, spec.getCategory(), idx, /* readFrom */ "Requests");
                        payload = new com.google.gson.Gson().toJson(requestList);
                        String resp = ResourceManagerServiceUtils.updateRequests(
                                instanceId, requestList, categories, idx);
                        ChangeRecord cr = recordFromRmResp(spec, idx, "requests", payload, resp);
                        changes.add(cr);
                        if (!cr.isSuccess()) {
                            return fail(changes, "update_requests failed: " + cr.getMessage(), startTs);
                        }
                    } catch (Exception e) {
                        log.error("update_requests threw", e);
                        changes.add(exceptionRecord(spec, idx, "requests",
                                payload == null ? "" : payload, e));
                        return fail(changes, "update_requests threw: " + e.getMessage(), startTs);
                    }
                }

                // 4.3 limits（同上）
                {
                    String payload = null;
                    try {
                        Map<String, String> limitList = buildResourceList(
                                spec.getCpuLimit(), spec.getMemoryLimit(),
                                nodeMap, spec.getCategory(), idx, /* readFrom */ "Limits");
                        payload = new com.google.gson.Gson().toJson(limitList);
                        String resp = ResourceManagerServiceUtils.updateLimits(
                                instanceId, limitList, categories, idx);
                        ChangeRecord cr = recordFromRmResp(spec, idx, "limits", payload, resp);
                        changes.add(cr);
                        if (!cr.isSuccess()) {
                            return fail(changes, "update_limits failed: " + cr.getMessage(), startTs);
                        }
                    } catch (Exception e) {
                        log.error("update_limits threw", e);
                        changes.add(exceptionRecord(spec, idx, "limits",
                                payload == null ? "" : payload, e));
                        return fail(changes, "update_limits threw: " + e.getMessage(), startTs);
                    }
                }
            }
        }

        // 5) 全部 update 成功后 restart，等待 RUNNING 让配置真正生效
        RestartInstanceParams restartParams = new RestartInstanceParams();
        restartParams.setInstanceId(instanceId);
        restartParams.setAccountEmail(params.getAccountEmail());
        restartParams.setAccountPassword(params.getAccountPassword());
        RestartInstanceResult restartResult = RestartInstanceComp.restartInstance(restartParams);

        int totalCost = (int) ((System.currentTimeMillis() - startTs) / 1000L);
        if (restartResult.getCommonResult() == null
                || !ResultEnum.SUCCESS.result.equalsIgnoreCase(restartResult.getCommonResult().getResult())) {
            String msg = restartResult.getCommonResult() == null
                    ? "restart result is null"
                    : restartResult.getCommonResult().getMessage();
            return UpdateInstanceComponentResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message("updates ok but restart failed: " + msg).build())
                    .changes(changes)
                    .costSeconds(totalCost)
                    .build();
        }

        return UpdateInstanceComponentResult.builder()
                .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                .changes(changes)
                .costSeconds(totalCost)
                .build();
    }

    // ----------------------------- helpers -----------------------------

    /** 前端 el-input 默认空串，这里把空/纯空白串归一化为 null 以便后续按"未指定"处理。 */
    private static String nullIfBlank(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static String validate(UpdateInstanceComponentParams params) {
        if (params == null) return "params is null";
        if (params.getInstanceId() == null || params.getInstanceId().isEmpty()) {
            return "instanceId is required";
        }
        if (params.getSpecs() == null || params.getSpecs().isEmpty()) {
            return "specs is required";
        }
        for (int i = 0; i < params.getSpecs().size(); i++) {
            ComponentSpec s = params.getSpecs().get(i);
            if (s.getCategory() == null || s.getCategory().isEmpty()) {
                return "specs[" + i + "].category is required";
            }
            // 允许一个 spec 什么字段都不填：此时 replicas 分支 no-op，requests / limits
            // 会用 describe 的当前值全量回填再下发，配合 restart 等于"强制 resync + 重启"。
        }
        return null;
    }

    /**
     * 拉 describe，按 (Role, ReplicaIndex) 建索引。
     * 注意这里**不再**放"任意副本组"的兜底 null key —— RM 的 update_replicas/requests/limits
     * 在 DAO 层用 `replica_index = NULL` 等值查询永远匹配不上，必须上游把 null 展开成真实的
     * replicaIndex 列表再下发，见 {@link #resolveReplicaIndexes}。
     */
    private static Map<NodeKey, JSONObject> loadNodeMap(String instanceId) {
        String descResp = ResourceManagerServiceUtils.describeInstance(instanceId);
        JSONObject respJo = JSONObject.parseObject(descResp);
        if (respJo == null || respJo.getInteger("Code") == null || respJo.getInteger("Code") != 0) {
            String msg = respJo == null ? "null response" : respJo.getString("Message");
            throw new RuntimeException("describe code != 0: " + msg);
        }
        JSONObject data = respJo.getJSONObject("Data");
        if (data == null) {
            throw new RuntimeException("describe Data is null");
        }
        JSONArray nodes = data.getJSONArray("Nodes");
        Map<NodeKey, JSONObject> out = new HashMap<>();
        if (nodes == null) return out;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String role = node.getString("Role");
            Integer idx = node.getInteger("ReplicaIndex");
            out.putIfAbsent(new NodeKey(role, idx), node);
        }
        return out;
    }

    /**
     * 把 spec.replicaIndex 展开成一组"真正存在"的 replicaIndex：
     * <ul>
     *   <li>spec.replicaIndex 非 null → 直接返回 [replicaIndex]</li>
     *   <li>spec.replicaIndex == null → 默认返回 [1]。
     *       原因：streamingNode / queryNode 在 DB 中预创建了 10 条记录（replicaIndex 1~10），
     *       但活跃副本组默认从 1 开始，传 null 给 RM 会导致 SQL 匹配不上而静默无操作。</li>
     * </ul>
     */
    private static List<Integer> resolveReplicaIndexes(ComponentSpec spec,
                                                        Map<NodeKey, JSONObject> nodeMap) {
        if (spec.getReplicaIndex() != null) {
            return Collections.singletonList(spec.getReplicaIndex());
        }
        // replicaIndex 未传时默认 1（replicaIndex 取值 1~10，代表第 1~第 10 副本组）
        return Collections.singletonList(1);
    }

    /**
     * 组装一次 update_requests / update_limits 的 resourceList。
     * <ul>
     *   <li>两个都指定了 → 直接使用</li>
     *   <li>只指定了一个 → 从 describe 结果回填另一个，避免 RM 把未传的那一维清空</li>
     *   <li>两个都没指定 → 从 describe 全量回填 cpu + memory，照样调 RM 接口
     *       （配合后续 restart 把配置重新推到 pod）</li>
     * </ul>
     * describe 里找不到对应 node / 资源字段时抛 RuntimeException，由调用方走快速失败路径。
     *
     * @param readFrom "Requests" 或 "Limits"，对应 describe node 里的 map 字段名
     */
    private static Map<String, String> buildResourceList(String cpu, String memory,
                                                          Map<NodeKey, JSONObject> nodeMap,
                                                          String category,
                                                          Integer replicaIndex,
                                                          String readFrom) {
        Map<String, String> out = new LinkedHashMap<>();
        if (cpu != null) out.put("cpu", cpu);
        if (memory != null) out.put("memory", memory);

        // 任意一维缺失都需要从 describe 回填
        if (cpu == null || memory == null) {
            JSONObject node = nodeMap.get(new NodeKey(category, replicaIndex));
            if (node == null) {
                throw new RuntimeException(String.format(
                        "can not locate node for category=%s replicaIndex=%s when building %s",
                        category, replicaIndex, readFrom));
            }
            JSONObject resourceMap = node.getJSONObject(readFrom);
            if (resourceMap == null) {
                throw new RuntimeException(String.format(
                        "describe node has no %s field for category=%s replicaIndex=%s",
                        readFrom, category, replicaIndex));
            }
            if (cpu == null) {
                String currentCpu = resourceMap.getString("cpu");
                if (currentCpu == null) {
                    throw new RuntimeException(String.format(
                            "describe has no current cpu in %s for category=%s replicaIndex=%s",
                            readFrom, category, replicaIndex));
                }
                out.put("cpu", currentCpu);
            }
            if (memory == null) {
                String currentMem = resourceMap.getString("memory");
                if (currentMem == null) {
                    throw new RuntimeException(String.format(
                            "describe has no current memory in %s for category=%s replicaIndex=%s",
                            readFrom, category, replicaIndex));
                }
                out.put("memory", currentMem);
            }
        }
        // 保证 cpu 在前、memory 在后，便于日志观察
        Map<String, String> ordered = new LinkedHashMap<>();
        if (out.containsKey("cpu")) ordered.put("cpu", out.get("cpu"));
        if (out.containsKey("memory")) ordered.put("memory", out.get("memory"));
        return ordered;
    }

    private static ChangeRecord recordFromRmResp(ComponentSpec spec, Integer replicaIndex,
                                                  String action, String payload, String rmResp) {
        JSONObject jo = JSONObject.parseObject(rmResp);
        boolean success = jo != null && jo.getInteger("Code") != null && jo.getInteger("Code") == 0;
        String msg = (jo == null) ? "null response" : jo.getString("Message");
        return ChangeRecord.builder()
                .category(spec.getCategory())
                .replicaIndex(replicaIndex)
                .action(action)
                .payload(payload)
                .success(success)
                .message(success ? null : msg)
                .build();
    }

    private static ChangeRecord exceptionRecord(ComponentSpec spec, Integer replicaIndex,
                                                 String action, String payload, Exception e) {
        return ChangeRecord.builder()
                .category(spec.getCategory())
                .replicaIndex(replicaIndex)
                .action(action)
                .payload(payload)
                .success(false)
                .message(e.getMessage())
                .build();
    }

    private static UpdateInstanceComponentResult fail(List<ChangeRecord> changes, String msg, long startTs) {
        return UpdateInstanceComponentResult.builder()
                .commonResult(CommonResult.builder()
                        .result(ResultEnum.WARNING.result)
                        .message(msg).build())
                .changes(changes)
                .costSeconds((int) ((System.currentTimeMillis() - startTs) / 1000L))
                .build();
    }

    /** 索引 key，支持 replicaIndex 为 null 的场景。 */
    private static final class NodeKey {
        final String role;
        final Integer replicaIndex;

        NodeKey(String role, Integer replicaIndex) {
            this.role = role;
            this.replicaIndex = replicaIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodeKey)) return false;
            NodeKey k = (NodeKey) o;
            return Objects.equals(role, k.role) && Objects.equals(replicaIndex, k.replicaIndex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(role, replicaIndex);
        }
    }
}
