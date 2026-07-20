package custom.components;

import custom.BaseTest;
import custom.entity.ChaosMeshParams;
import custom.entity.result.ChaosMeshResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.utils.KubernetesUtils;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Creates and deletes bounded namespaced Chaos Mesh custom resources. */
@Slf4j
public class ChaosMeshComp {
    private static final String GROUP = "chaos-mesh.org";
    private static final String VERSION = "v1alpha1";
    private static final Set<String> SUPPORTED_KINDS = Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(Arrays.asList(
                    "PodChaos", "NetworkChaos", "StressChaos", "TimeChaos", "IOChaos")));

    private ChaosMeshComp() {
    }

    public static ChaosMeshResult execute(ChaosMeshParams params) {
        String operation = normalizeOperation(params == null ? null : params.getOperation());
        try {
            resolveNamespace(params);
            validate(params, operation);
            Map<String, Object> resource = "create".equals(operation)
                    ? buildResource(params) : buildIdentity(params);

            if (params.isDryRun()) {
                log.info("Chaos Mesh dry run: operation={}, kind={}, namespace={}, name={}",
                        operation, params.getKind(), params.getNamespace(), params.getName());
                return success(params, operation, resource);
            }

            ApiClient client = KubernetesUtils.createApiClient(resolveKubeConfigPath());
            CustomObjectsApi customObjectsApi = new CustomObjectsApi(client);
            Object response;
            if ("create".equals(operation)) {
                response = customObjectsApi.createNamespacedCustomObject(
                        GROUP, VERSION, params.getNamespace(), pluralFor(params.getKind()), resource,
                        null, null, null);
            } else {
                response = customObjectsApi.deleteNamespacedCustomObject(
                        GROUP, VERSION, params.getNamespace(), pluralFor(params.getKind()), params.getName(),
                        null, null, null, null, new V1DeleteOptions());
            }
            return success(params, operation, response);
        } catch (IllegalArgumentException e) {
            return ChaosMeshResult.builder()
                    .operation(operation)
                    .kind(params == null ? null : params.getKind())
                    .namespace(params == null ? null : params.getNamespace())
                    .name(params == null ? null : params.getName())
                    .dryRun(params != null && params.isDryRun())
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.FAIL.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Chaos Mesh {} failed for {}/{}/{}", operation,
                    params == null ? null : params.getKind(),
                    params == null ? null : params.getNamespace(),
                    params == null ? null : params.getName(), e);
            return ChaosMeshResult.builder()
                    .operation(operation)
                    .kind(params == null ? null : params.getKind())
                    .namespace(params == null ? null : params.getNamespace())
                    .name(params == null ? null : params.getName())
                    .dryRun(params != null && params.isDryRun())
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }

    static Map<String, Object> buildResource(ChaosMeshParams params) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", params.getName());
        metadata.put("namespace", params.getNamespace());

        Map<String, Object> spec = new LinkedHashMap<>();
        if (params.getAttributes() != null) {
            spec.putAll(params.getAttributes());
        }
        spec.put("duration", params.getDuration());

        Map<String, Object> selector = new LinkedHashMap<>();
        selector.put("namespaces", Collections.singletonList(params.getNamespace()));
        selector.put("labelSelectors", new LinkedHashMap<>(params.getLabelSelectors()));
        spec.put("selector", selector);

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("apiVersion", GROUP + "/" + VERSION);
        resource.put("kind", params.getKind());
        resource.put("metadata", metadata);
        resource.put("spec", spec);
        return resource;
    }

    private static Map<String, Object> buildIdentity(ChaosMeshParams params) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", params.getName());
        metadata.put("namespace", params.getNamespace());

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("apiVersion", GROUP + "/" + VERSION);
        resource.put("kind", params.getKind());
        resource.put("metadata", metadata);
        return resource;
    }

    static String pluralFor(String kind) {
        validateKind(kind);
        return kind.toLowerCase(Locale.ROOT);
    }

    private static void validate(ChaosMeshParams params, String operation) {
        if (params == null) {
            throw new IllegalArgumentException("ChaosMeshParams must not be null");
        }
        if (!"create".equals(operation) && !"delete".equals(operation)) {
            throw new IllegalArgumentException("operation must be create or delete");
        }
        validateKind(params.getKind());
        requireText(params.getName(), "name");
        requireText(params.getNamespace(), "namespace (or instanceId / a previously created instance)");

        if ("create".equals(operation)) {
            requireText(params.getDuration(), "duration");
            if (!params.getDuration().matches("^(?=.*[1-9])(?:\\d+(?:ms|h|m|s))+$")) {
                throw new IllegalArgumentException("duration must be a positive Go duration, for example 60s or 2m");
            }
            if (params.getLabelSelectors() == null || params.getLabelSelectors().isEmpty()) {
                throw new IllegalArgumentException("labelSelectors must contain at least one target label");
            }
        }
    }

    private static void validateKind(String kind) {
        requireText(kind, "kind");
        if (!SUPPORTED_KINDS.contains(kind)) {
            throw new IllegalArgumentException("kind must be one of " + SUPPORTED_KINDS);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }

    private static String normalizeOperation(String operation) {
        return operation == null || operation.trim().isEmpty()
                ? "create" : operation.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveKubeConfigPath() {
        String override = System.getProperty("kubeconfig");
        if (override != null && !override.trim().isEmpty()) {
            return override;
        }
        return BaseTest.envEnum == null ? null : BaseTest.envEnum.kubeConfig;
    }

    private static void resolveNamespace(ChaosMeshParams params) {
        if (params == null || hasText(params.getNamespace())) {
            return;
        }
        String instanceId = hasText(params.getInstanceId())
                ? params.getInstanceId()
                : (BaseTest.newInstanceInfo == null ? null : BaseTest.newInstanceInfo.getInstanceId());
        if (!hasText(instanceId)) {
            return;
        }
        String normalizedInstanceId = instanceId.trim();
        params.setInstanceId(normalizedInstanceId);
        params.setNamespace(normalizedInstanceId.startsWith("milvus-")
                ? normalizedInstanceId : "milvus-" + normalizedInstanceId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static ChaosMeshResult success(ChaosMeshParams params, String operation, Object resource) {
        return ChaosMeshResult.builder()
                .operation(operation)
                .kind(params.getKind())
                .namespace(params.getNamespace())
                .name(params.getName())
                .dryRun(params.isDryRun())
                .resource(resource)
                .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                .build();
    }
}
