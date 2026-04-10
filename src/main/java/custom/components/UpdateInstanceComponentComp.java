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
        for (ComponentSpec spec : params.getSpecs()) {
            List<String> categories = Collections.singletonList(spec.getCategory());

            // 4.1 replicas
            if (spec.getReplicas() != null) {
                String payload = String.format("{\"replicas\":%d}", spec.getReplicas());
                try {
                    String resp = ResourceManagerServiceUtils.updateReplicas(
                            instanceId, spec.getReplicas(), categories, spec.getReplicaIndex());
                    ChangeRecord cr = recordFromRmResp(spec, "replicas", payload, resp);
                    changes.add(cr);
                    if (!cr.isSuccess()) {
                        return fail(changes, "update_replicas failed: " + cr.getMessage(), startTs);
                    }
                } catch (Exception e) {
                    log.error("update_replicas threw", e);
                    changes.add(exceptionRecord(spec, "replicas", payload, e));
                    return fail(changes, "update_replicas threw: " + e.getMessage(), startTs);
                }
            }

            // 4.2 requests（cpu + memory 必须成对传给 RM，缺的那一维用 describe 回填）
            Map<String, String> requestList = buildResourceList(
                    spec.getCpuRequest(), spec.getMemoryRequest(),
                    nodeMap, spec, /* readFrom */ "Requests");
            if (requestList != null) {
                String payload = new com.google.gson.Gson().toJson(requestList);
                try {
                    String resp = ResourceManagerServiceUtils.updateRequests(
                            instanceId, requestList, categories, spec.getReplicaIndex());
                    ChangeRecord cr = recordFromRmResp(spec, "requests", payload, resp);
                    changes.add(cr);
                    if (!cr.isSuccess()) {
                        return fail(changes, "update_requests failed: " + cr.getMessage(), startTs);
                    }
                } catch (Exception e) {
                    log.error("update_requests threw", e);
                    changes.add(exceptionRecord(spec, "requests", payload, e));
                    return fail(changes, "update_requests threw: " + e.getMessage(), startTs);
                }
            }

            // 4.3 limits
            Map<String, String> limitList = buildResourceList(
                    spec.getCpuLimit(), spec.getMemoryLimit(),
                    nodeMap, spec, /* readFrom */ "Limits");
            if (limitList != null) {
                String payload = new com.google.gson.Gson().toJson(limitList);
                try {
                    String resp = ResourceManagerServiceUtils.updateLimits(
                            instanceId, limitList, categories, spec.getReplicaIndex());
                    ChangeRecord cr = recordFromRmResp(spec, "limits", payload, resp);
                    changes.add(cr);
                    if (!cr.isSuccess()) {
                        return fail(changes, "update_limits failed: " + cr.getMessage(), startTs);
                    }
                } catch (Exception e) {
                    log.error("update_limits threw", e);
                    changes.add(exceptionRecord(spec, "limits", payload, e));
                    return fail(changes, "update_limits threw: " + e.getMessage(), startTs);
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
            boolean anyChange = s.getReplicas() != null
                    || s.getCpuRequest() != null || s.getMemoryRequest() != null
                    || s.getCpuLimit() != null || s.getMemoryLimit() != null;
            if (!anyChange) {
                return "specs[" + i + "] (" + s.getCategory() + ") has no field to update";
            }
        }
        return null;
    }

    /**
     * 拉 describe，按 (Role, ReplicaIndex) 建索引；
     * 对于 replicaIndex 为 null 的 spec，我们用特殊 key null 表示"任意组"并映射到第一个命中的 node，
     * 回填时够用（因为 RM 也是对所有副本组统一下发一组资源）。
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
            // 同时放一个"任意副本组"的兜底项，便于 replicaIndex==null 的 spec 命中
            out.putIfAbsent(new NodeKey(role, null), node);
        }
        return out;
    }

    /**
     * 组装一次 update_requests / update_limits 的 resourceList。
     * <ul>
     *   <li>cpu 和 memory 都没指定 → 返回 null，表示本 spec 不需要调这个接口</li>
     *   <li>只指定了其中一个 → 从 describe 结果里回填另一个，避免 RM 把没传的那一维清空</li>
     *   <li>两个都指定了 → 直接使用</li>
     * </ul>
     * 如果需要回填但 describe 里找不到对应 node 或对应字段，返回 null 并打 warn，
     * 让调用方知道这个维度跳过了（而不是发送一个会把值清空的危险 payload）。
     *
     * @param readFrom "Requests" 或 "Limits"，对应 describe node 里的 map 字段名
     */
    private static Map<String, String> buildResourceList(String cpu, String memory,
                                                          Map<NodeKey, JSONObject> nodeMap,
                                                          ComponentSpec spec,
                                                          String readFrom) {
        if (cpu == null && memory == null) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        if (cpu != null) out.put("cpu", cpu);
        if (memory != null) out.put("memory", memory);

        // 需要回填
        if (cpu == null || memory == null) {
            JSONObject node = nodeMap.get(new NodeKey(spec.getCategory(), spec.getReplicaIndex()));
            if (node == null) {
                log.warn("can not locate node for category={} replicaIndex={}, skip update_{}",
                        spec.getCategory(), spec.getReplicaIndex(), readFrom.toLowerCase());
                return null;
            }
            JSONObject resourceMap = node.getJSONObject(readFrom);
            if (resourceMap == null) {
                log.warn("describe node has no {} field for category={} replicaIndex={}, skip",
                        readFrom, spec.getCategory(), spec.getReplicaIndex());
                return null;
            }
            if (cpu == null) {
                String currentCpu = resourceMap.getString("cpu");
                if (currentCpu == null) {
                    log.warn("describe has no current cpu for category={}, skip update_{}",
                            spec.getCategory(), readFrom.toLowerCase());
                    return null;
                }
                out.put("cpu", currentCpu);
            }
            if (memory == null) {
                String currentMem = resourceMap.getString("memory");
                if (currentMem == null) {
                    log.warn("describe has no current memory for category={}, skip update_{}",
                            spec.getCategory(), readFrom.toLowerCase());
                    return null;
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

    private static ChangeRecord recordFromRmResp(ComponentSpec spec, String action,
                                                  String payload, String rmResp) {
        JSONObject jo = JSONObject.parseObject(rmResp);
        boolean success = jo != null && jo.getInteger("Code") != null && jo.getInteger("Code") == 0;
        String msg = (jo == null) ? "null response" : jo.getString("Message");
        return ChangeRecord.builder()
                .category(spec.getCategory())
                .replicaIndex(spec.getReplicaIndex())
                .action(action)
                .payload(payload)
                .success(success)
                .message(success ? null : msg)
                .build();
    }

    private static ChangeRecord exceptionRecord(ComponentSpec spec, String action,
                                                 String payload, Exception e) {
        return ChangeRecord.builder()
                .category(spec.getCategory())
                .replicaIndex(spec.getReplicaIndex())
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
