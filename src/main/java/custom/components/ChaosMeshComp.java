package custom.components;

import custom.BaseTest;
import custom.entity.ChaosMeshParams;
import custom.entity.result.ChaosMeshResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.utils.KubernetesUtils;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
            generateName(params, operation);
            validate(params, operation);
            Map<String, Object> resource = "create".equals(operation)
                    ? buildResource(params) : buildIdentity(params);

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
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(exceptionMessage(e))
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
            if ("PodChaos".equals(params.getKind()) && "container-kill".equals(attributeText(params, "action"))
                    && !hasContainerNames(params)) {
                throw new IllegalArgumentException("attributes.containerNames must contain at least one container for container-kill");
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

    private static void generateName(ChaosMeshParams params, String operation) {
        if (params == null || !"create".equals(operation) || hasText(params.getName())) {
            return;
        }
        String kind = hasText(params.getKind()) ? params.getKind().trim().toLowerCase(Locale.ROOT) : "experiment";
        params.setName("chaos-" + kind + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String attributeText(ChaosMeshParams params, String key) {
        if (params.getAttributes() == null) {
            return null;
        }
        Object value = params.getAttributes().get(key);
        return value instanceof String ? (String) value : null;
    }

    private static boolean hasContainerNames(ChaosMeshParams params) {
        Object containerNames = params.getAttributes() == null ? null : params.getAttributes().get("containerNames");
        if (!(containerNames instanceof Iterable)) {
            return false;
        }
        for (Object containerName : (Iterable<?>) containerNames) {
            if (containerName instanceof String && hasText((String) containerName)) {
                return true;
            }
        }
        return false;
    }

    private static String exceptionMessage(Exception e) {
        if (e instanceof ApiException) {
            ApiException apiException = (ApiException) e;
            String responseBody = apiException.getResponseBody();
            if (hasText(responseBody)) {
                return "Kubernetes API " + apiException.getCode() + ": " + responseBody;
            }
            return "Kubernetes API " + apiException.getCode() + ": " + apiException.getMessage();
        }
        return hasText(e.getMessage()) ? e.getMessage() : e.toString();
    }

    private static ChaosMeshResult success(ChaosMeshParams params, String operation, Object response) {
        return ChaosMeshResult.builder()
                .operation(operation)
                .kind(params.getKind())
                .namespace(params.getNamespace())
                .name(params.getName())
                .affectedPods(extractAffectedPods(response))
                .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                .build();
    }

    private static List<String> extractAffectedPods(Object response) {
        if (!(response instanceof Map)) {
            return Collections.emptyList();
        }
        Object status = ((Map<?, ?>) response).get("status");
        if (!(status instanceof Map)) {
            return Collections.emptyList();
        }
        Object experiment = ((Map<?, ?>) status).get("experiment");
        if (!(experiment instanceof Map)) {
            return Collections.emptyList();
        }
        Object containerRecords = ((Map<?, ?>) experiment).get("containerRecords");
        if (!(containerRecords instanceof Iterable)) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> affectedPods = new LinkedHashSet<>();
        for (Object record : (Iterable<?>) containerRecords) {
            if (!(record instanceof Map)) {
                continue;
            }
            Object id = ((Map<?, ?>) record).get("id");
            if (!(id instanceof String) || ((String) id).trim().isEmpty()) {
                continue;
            }
            String podIdentifier = ((String) id).trim();
            int separator = podIdentifier.lastIndexOf('/');
            affectedPods.add(separator >= 0 ? podIdentifier.substring(separator + 1) : podIdentifier);
        }
        return new ArrayList<>(affectedPods);
    }
}
