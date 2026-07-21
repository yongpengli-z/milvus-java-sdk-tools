## 1. Component contract

- [x] 1.1 Add `ChaosMeshParams` and `ChaosMeshResult` models with create/delete, target, duration, selector, and attributes fields.
- [x] 1.2 Add local validation and Chaos Mesh kind-to-resource mapping for the supported experiment types.

## 2. Kubernetes integration

- [x] 2.1 Implement creation and deletion with the existing Kubernetes Java client and selected kubeconfig.
- [x] 2.2 Register the component with `ComponentSchedule` and preserve results in the existing reporting path.
- [x] 2.3 Resolve a missing Chaos Mesh namespace from an explicit or globally tracked instance ID.
- [x] 2.4 Remove the local dry-run option from the UI, API contract, results, documentation, and examples.
- [x] 2.5 Generate a name when a create operation omits one, and clean up successful Chaos Mesh resources at the end of the outer scenario.

## 3. Documentation and verification

- [x] 3.1 Document `ChaosMeshParams`, add it to the README component index, and include a bounded PodChaos example scenario.
- [x] 3.2 Build the project and verify the new component compiles cleanly.
