package custom.entity;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for a bounded Chaos Mesh experiment.
 *
 * <p>The component creates a namespaced custom resource in the
 * {@code chaos-mesh.org/v1alpha1} API. Type-specific Chaos Mesh fields belong
 * in {@link #attributes}; namespace, duration and selector are controlled by
 * this envelope so a scenario cannot accidentally create an unbounded target.
 */
@Data
public class ChaosMeshParams {
    /** {@code create} (default) or {@code delete}. */
    private String operation = "create";

    /** PodChaos, NetworkChaos, StressChaos, TimeChaos, or IOChaos. */
    private String kind;

    /** Kubernetes custom resource name. */
    private String name;

    /**
     * Optional Milvus instance ID. When namespace is empty, the component
     * targets {@code milvus-<instanceId>}; when also empty it falls back to
     * the instance tracked by an earlier CreateInstance step.
     */
    private String instanceId;

    /** Namespace containing both the Chaos Mesh resource and target Pods. */
    private String namespace;

    /** Go duration string, for example {@code 60s} or {@code 2m}. Required for create. */
    private String duration;

    /** Target Pod labels. At least one selector is required for create. */
    private Map<String, String> labelSelectors = new LinkedHashMap<>();

    /** Type-specific Chaos Mesh spec fields, for example {@code action: pod-failure}. */
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /** Validate and return the generated resource without contacting Kubernetes. */
    private boolean dryRun;
}
