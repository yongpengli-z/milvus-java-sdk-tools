## Context

The runner turns numbered JSON entries into `custom.entity.*Params` classes and dispatches them from `ComponentSchedule`.  It already ships the Kubernetes Java client and has `KubernetesUtils` for loading the kubeconfig selected by `EnvEnum`.  AWS UAT has a functioning Chaos Mesh installation and its CRDs use the `chaos-mesh.org/v1alpha1` API group/version.

## Goals / Non-Goals

**Goals:**

- Add one ordered scenario component that can create or remove a bounded, namespaced Chaos Mesh custom resource.
- Make the target and lifetime explicit in JSON so a test report identifies exactly which fault was requested.
- Use the existing kubeconfig selection and Kubernetes Java client.
- Keep failure recovery simple: deleting the experiment resource stops the injected fault.

**Non-Goals:**

- Installing, upgrading, or configuring Chaos Mesh.
- Creating a dashboard UI, workflows, schedules, or cluster-scoped experiments.
- Automatically selecting a production target or silently cleaning up an experiment outside the requested scenario.
- Implementing typed Java models for every Chaos Mesh CRD.

## Decisions

### Use a generic custom-resource envelope

`ChaosMeshParams` will contain `operation`, `kind`, `name`, `namespace`, `duration`, `selector`, and an `attributes` map.  The component constructs the CR body and invokes `CustomObjectsApi`; `kind` is constrained to PodChaos, NetworkChaos, StressChaos, TimeChaos, and IOChaos.  This gives the scenario schema stable safety fields while allowing type-specific Chaos Mesh fields such as `action`, `delay`, `loss`, or `stressors` to pass through in `attributes`.

Alternatives considered:

- A raw YAML string would avoid mapping work but would be difficult to validate and easy to target outside the intended namespace.
- Separate parameter/component classes per CRD would provide exhaustive typing but introduce a large, fast-changing API surface for the initial integration.

### Require an explicit bounded target

Create operations will require a namespace, a non-empty name, a supported kind, a positive duration, and at least one label selector.  The generated CR sets `spec.selector.namespaces` to the component namespace, preventing the initial component from selecting another namespace.  Delete operations require only the resource identity.

### Resolve the namespace from the scenario instance when omitted

`ChaosMeshParams` accepts an optional `instanceId`. The component resolves its namespace in this order: an explicitly supplied namespace, an explicit instance ID converted to `milvus-<instanceId>`, then `BaseTest.newInstanceInfo.instanceId` converted to the same convention. If none are available, validation fails before a Kubernetes request. This mirrors existing instance-management components and lets a `CreateInstanceParams` step be followed directly by a Chaos Mesh step.

### Provide no-mutation validation mode

`dryRun=true` performs all local validation and returns the generated resource body without contacting the Kubernetes API.  It is intentionally a local dry run because the Kubernetes client version's custom-object server-side dry-run overload is not consistently available across the project's supported client methods.

### Dispatch and report like existing components

`ComponentSchedule` will dispatch `ChaosMeshParams`, store a `ChaosMeshResult`, and report it under the numbered component name.  Kubernetes API errors are captured as an exception result rather than escaping without a scenario report entry.

## Risks / Trade-offs

- [Chaos action disrupts an incorrect workload] → namespace, selector, duration, and supported kind are mandatory; dry-run is available for configuration review.
- [A test exits before its cleanup step] → documentation requires a paired delete step and reports the resource name needed for manual deletion.
- [Chaos Mesh CRD schema changes] → the component leaves type-specific fields in an extensible attributes map and limits its contract to the common envelope.
- [Kubeconfig does not point at a Chaos Mesh-enabled cluster] → Kubernetes API errors are returned with actionable messages; no install attempt is made.

## Migration Plan

This is additive.  Deploy the new runner, first run a `dryRun` configuration, then create a short-duration experiment followed by a delete operation.  Rolling back only requires using a prior runner; any active experiment must be deleted explicitly with the resource identity reported by the component.

## Open Questions

- None for the initial component; future work can add typed experiment models and dashboard/workflow support if callers need them.
