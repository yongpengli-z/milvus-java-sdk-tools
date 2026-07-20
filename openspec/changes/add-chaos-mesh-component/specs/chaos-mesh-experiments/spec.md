## ADDED Requirements

### Requirement: Ordered scenario can create a Chaos Mesh experiment
The test runner SHALL accept `ChaosMeshParams` as an ordered scenario step and SHALL create a namespaced Chaos Mesh custom resource in API group `chaos-mesh.org`, version `v1alpha1`, when `operation` is `create`.

#### Scenario: Create a bounded PodChaos experiment
- **WHEN** a scenario supplies a valid `ChaosMeshParams` create operation for `PodChaos` with a namespace, name, positive duration, and label selector
- **THEN** the runner SHALL create a `podchaos` custom resource in that namespace and report its identity and requested status.

### Requirement: Experiment target is explicitly bounded
The component SHALL accept only PodChaos, NetworkChaos, StressChaos, TimeChaos, and IOChaos create operations.  It SHALL require a namespace, name, positive duration, and at least one label selector, and SHALL set the Chaos Mesh selector namespace to the component namespace.

#### Scenario: Reject an unbounded experiment
- **WHEN** a create operation omits its duration or label selector
- **THEN** the component SHALL return a failed result without creating a Kubernetes resource.

### Requirement: Scenario can remove a Chaos Mesh experiment
The test runner SHALL delete the custom resource identified by a valid `ChaosMeshParams` delete operation and SHALL report the deletion result.

#### Scenario: Delete an experiment after a test step
- **WHEN** a scenario supplies a delete operation with the kind, namespace, and name of a created experiment
- **THEN** the runner SHALL delete that custom resource so Chaos Mesh can stop the injected fault.

### Requirement: Dry run does not mutate the cluster
When `dryRun` is true, the component SHALL validate the supplied create operation and return the generated custom-resource body without calling the Kubernetes API.

#### Scenario: Preview a NetworkChaos experiment
- **WHEN** a scenario supplies a valid NetworkChaos create operation with `dryRun` enabled
- **THEN** the result SHALL be successful and contain the generated resource body, and no Kubernetes custom resource SHALL be created.

### Requirement: Component results are usable in a test report
The component SHALL report its operation, kind, namespace, resource name, generated or returned resource body, and a success or exception outcome through the existing component result path.

#### Scenario: Kubernetes API rejects an experiment
- **WHEN** Kubernetes rejects a validly structured create or delete request
- **THEN** the component SHALL return and report an exception result containing the API error message.

### Requirement: Experiment namespace can use the scenario instance
When `namespace` is omitted, the component SHALL derive it as `milvus-<instanceId>` using an explicit `instanceId`, or the `BaseTest.newInstanceInfo.instanceId` value created or connected earlier in the same scenario. An explicit `namespace` SHALL take precedence.

#### Scenario: Use an instance created by an earlier step
- **WHEN** a Chaos Mesh experiment omits both namespace and instance ID after a successful CreateInstance step
- **THEN** the component SHALL target the namespace derived from the globally tracked instance ID.
