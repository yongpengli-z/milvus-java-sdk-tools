## Why

The test runner can create and manage Milvus workloads but cannot inject a controlled Kubernetes failure as part of the same ordered test scenario.  Chaos Mesh is already installed and healthy in the AWS UAT cluster, so exposing it as a component enables repeatable resilience tests alongside workload and assertion steps.

## What Changes

- Add a `ChaosMeshParams` scenario component that creates one namespaced Chaos Mesh custom resource through the Kubernetes Java client.
- Support the initial Chaos Mesh experiment types needed for safe, targeted smoke tests: Pod, Network, Stress, Time, and IO chaos.
- Require an explicit target namespace and selector with an optional duration.
- Allow callers to omit an Instance ID and resolve the target namespace from the globally tracked instance created earlier in the same scenario.
- Return the created custom resource identity and observed status so a scenario report records the injected fault.
- Add documentation and an example configuration for a bounded PodChaos experiment.

## Capabilities

### New Capabilities

- `chaos-mesh-experiments`: Create and report bounded Chaos Mesh experiments from ordered test-runner scenarios.

### Modified Capabilities

- None.

## Impact

- Adds parameter, result, component, scheduler dispatch, and Kubernetes custom-resource utility code under `src/main/java/custom`.
- Uses the existing Kubernetes Java client and the kubeconfig selected by `EnvEnum`; no dependency changes are expected.
- Adds component documentation, a README component index entry, and an example scenario JSON.
