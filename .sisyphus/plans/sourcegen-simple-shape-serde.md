# SourceGen Build-Time-Only Routing + JMH Validation + Deserializer Parity Extension

## TL;DR
> **Summary**: Re-scope SourceGen work to build-time decisioning only, then extend with deserializer SourceGen parity optimizations (constants + createSpecific specialization + cached child deserializers) to mirror serializer build-time optimizations.
> **Deliverables**:
> - Remove backend-mode API/config/runtime resolver surfaces
> - Simplify `DefaultSerdeRegistry` to metadata-only generated-vs-introspection selection
> - Preserve serializer/deserializer behavior parity via processor-time eligibility metadata
> - Add JMH benchmarks + runbook to measure throughput/latency impact
> - Optimize generated deserializers (bean/record/enum) with constants + `createSpecific` caching parity
> **Effort**: Large
> **Parallel**: YES - 6 waves
> **Critical Path**: Task 1 → Task 3 → Task 5 → Task 7 → Task 10 → Task 11 → Task 15

## Context
### Original Request
- Reduce `DefaultSerdeRegistry` changes and keep this as a build-time optimization.
- Remove runtime backend selection configuration.
- Remove `shouldBypassGeneratedSerializer`-style runtime heuristics.
- Keep runtime dispatch minimal using build-time metadata.
- Update this plan to include JMH benchmarks for value/throughput validation.
- Add deserializer build-time optimizations equivalent to serializer commits `6340a498ff1f02b07a59d88173ab0ff37ef111bf` and `88b8676d5160a41c036ecb1337175526e219a701`, including Argument/name constants and `createSpecific` child-deserializer caching.

### Interview Summary
- Decided: **remove API + runtime backend config** (not keep/deprecate).
- Decided: **add benchmarks + runbook** (no CI performance threshold gate).
- Default applied: if metadata declares generated serde but generated class cannot be loaded, fail fast with `SerdeException` (no silent fallback).
- Desired runtime routing model:
  - if build-time metadata marks generated serializer/deserializer available, use generated class
  - else fallback to introspection path
- New scope decision: mirror serializer optimization architecture for generated deserializers via static constants + specialized constructors + `createSpecific` prewiring + lazy fallback when default-constructed.
- Default applied: include guarded scalar decode/default optimization as a later-wave task only after parity + semantic-lock tests.

### Metis Review (gaps addressed)
- Incorporated guardrails for scope control (no replacement runtime backend switch).
- Added explicit removal-validation checks (grep-based acceptance criteria).
- Added missing negative-path test: generated class metadata present but class load failure behavior.
- Added benchmark execution evidence requirements under existing `benchmarks` module conventions.
- Added deserializer parity guardrails: no static caching of context-sensitive child deserializers, preserve property-path wrapping + unknown/duplicate/default/null semantics, and verify both specialized and default-constructor execution paths.

## Work Objectives
### Core Objective
Make generated/introspection routing build-time-driven and deterministic while minimizing runtime policy logic and preserving test-suite behavior.

### Deliverables
- Backend-mode surface removal:
  - `serde-api` backend enum + config binding + annotation members + metadata keys
  - runtime resolver class and usages
- Runtime dispatch simplification in `DefaultSerdeRegistry` using only SourceGen metadata keys.
- Updated routing/regression tests aligned to new model.
- New JMH benchmark coverage and execution runbook.
- Deserializer SourceGen parity optimizations in bean/record/enum generators (constants + `createSpecific` specialization + cached child deserializers with lazy fallback).
- Deserializer-focused regression/shape tests proving semantic equivalence and optimization activation.

### Definition of Done (verifiable)
- [x] `./gradlew test` passes.
- [x] `./gradlew :benchmarks:jmh` passes.
- [x] `grep -R "micronaut\.serde\.backend-mode" -n .` returns no matches in active code/tests/docs.
- [x] `grep -R "SerdeBackendModeResolver\|SerdeBackendMode" -n serde-api serde-support serde-jackson serde-processor` returns no active backend-mode runtime/config references.
- [x] `DefaultSerdeRegistry` no longer contains `shouldBypassGeneratedSerializer` / `shouldBypassGeneratedDeserializer`.
- [ ] Generated bean/record/enum deserializers use static Argument/name constants and no per-field/component repeated lookup chain in hot loops.
- [ ] Deserializer semantic-lock tests pass for unknown/duplicate/null/default/property-path behavior in both specialized and default-constructor paths.

### Must Have
- Build-time metadata (`SOURCEGEN_*`) is single routing authority.
- Runtime selection logic remains simple and local to registry dispatch.
- JMH benchmarks measure both throughput and latency-oriented modes for representative serde fixtures.

### Must NOT Have
- No new runtime backend toggle replacing removed backend mode.
- No reintroduction of deep per-property runtime heuristics in registry.
- No CI performance threshold gate in this PR.

## Verification Strategy
> ZERO HUMAN INTERVENTION — all verification is agent-executed.

- Test decision: **tests-after** on each task + full-suite verification at end.
- QA policy: every task includes happy + failure/edge scenarios.
- Evidence path: `.sisyphus/evidence/task-{N}-{slug}.txt`

## Execution Strategy
### Parallel Execution Waves
Wave 1: API/config/runtime-surface removal foundation
- Task 1, Task 2

Wave 2: Runtime dispatch simplification
- Task 3, Task 4

Wave 3: Regression tests + behavior hardening
- Task 5, Task 6

Wave 4: Benchmarks + final verification
- Task 7, Task 8, Task 9

Wave 5: Deserializer SourceGen parity optimization
- Task 10, Task 11, Task 12, Task 13

Wave 6: Deserializer extension verification
- Task 14, Task 15

### Dependency Matrix
- Task 1 blocks Task 2/3/5
- Task 2 blocks Task 3
- Task 3 blocks Task 4/5
- Task 4 blocks Task 9
- Task 5 blocks Task 6/9
- Task 6 blocks Task 9
- Task 7 blocks Task 8/9
- Task 8 blocks Task 9
- Task 9 blocks Task 10
- Task 10 blocks Task 11/13
- Task 11 blocks Task 13/14
- Task 12 blocks Task 14
- Task 13 blocks Task 14/15
- Task 14 blocks Task 15

### Agent Dispatch Summary
- Wave 1: unspecified-high (API + metadata surface)
- Wave 2: deep (registry simplification correctness)
- Wave 3: unspecified-high (test parity/regression)
- Wave 4: unspecified-high + quick (bench + runbook + verification)
- Wave 5: deep + unspecified-high (deserializer generator optimization + semantic locks)
- Wave 6: unspecified-high (extension verification gate)

## TODOs

- [x] 1. Remove backend-mode API and configuration surfaces

  **What to do**:
  - Remove backend enum/type/config APIs and annotation members added for runtime backend routing:
    - `serde-api/src/main/java/io/micronaut/serde/config/SerdeBackendMode.java`
    - `serde-api/src/main/java/io/micronaut/serde/config/SerdeConfiguration.java` (`getBackendMode`)
    - `serde-api/src/main/java/io/micronaut/serde/config/DefaultSerdeConfiguration.java` backend-mode binding
    - `serde-api/src/main/java/io/micronaut/serde/annotation/Serdeable.java` backend members
  - Remove backend metadata constants from:
    - `serde-api/src/main/java/io/micronaut/serde/config/annotation/SerdeConfig.java` (`BACKEND`, `SERIALIZE_BACKEND`, `DESERIALIZE_BACKEND`)

  **Must NOT do**:
  - Must not remove `SOURCEGEN_*` metadata keys.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: public API surface changes across modules
  - Skills: `[]`
  - Omitted: `[frontend-ui-ux]` — not applicable

  **Parallelization**: Can Parallel: NO | Wave 1 | Blocks: 2,3,5 | Blocked By: none

  **References**:
  - `serde-api/src/main/java/io/micronaut/serde/config/SerdeConfiguration.java:145`
  - `serde-api/src/main/java/io/micronaut/serde/config/annotation/SerdeConfig.java:210-220`
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/annotation/SerdeBackendModeSpec.groovy`

  **Acceptance Criteria**:
  - [x] Backend-mode symbols are removed from `serde-api` compile surface.
  - [x] `:micronaut-serde-api:test` passes after test updates.

  **QA Scenarios**:
  ```
  Scenario: API removal happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-api:compileJava :micronaut-serde-api:test
    Expected: Build successful; no backend-mode compile references remain
    Evidence: .sisyphus/evidence/task-1-api-removal.txt

  Scenario: Edge check for stale backend config key
    Tool: Bash
    Steps: grep -R "micronaut\.serde\.backend-mode" -n serde-api serde-jackson serde-support serde-processor
    Expected: No matches
    Evidence: .sisyphus/evidence/task-1-no-backend-property.txt
  ```

  **Commit**: YES | Message: `refactor(serde-api): remove runtime backend mode surface` | Files: serde-api/*, impacted tests

- [x] 2. Remove backend mapping from processor mappers

  **What to do**:
  - Remove backend mapping logic from:
    - `serde-processor/.../serde/SerdeableMapper.java`
    - `serde-processor/.../serde/SerializableMapper.java`
    - `serde-processor/.../serde/DeserializableMapper.java`
  - Keep all non-backend mapping behavior unchanged.

  **Must NOT do**:
  - Must not alter mapping for `using`, `as`, `validate`, `naming`.

  **Recommended Agent Profile**:
  - Category: `quick` — Reason: targeted mapper key removal
  - Skills: `[]`
  - Omitted: `[git-master]` — not required for implementation

  **Parallelization**: Can Parallel: YES | Wave 1 | Blocks: 3 | Blocked By: 1

  **References**:
  - `serde-processor/src/main/java/io/micronaut/serde/processor/serde/SerdeableMapper.java:47-48`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/serde/SerializableMapper.java:54-55`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/serde/DeserializableMapper.java:54-55`

  **Acceptance Criteria**:
  - [x] Processor compiles and existing non-backend mapper behavior remains.

  **QA Scenarios**:
  ```
  Scenario: Processor mapping happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-processor:compileJava :micronaut-serde-processor:test
    Expected: Build successful; mapper tests pass
    Evidence: .sisyphus/evidence/task-2-processor-mappers.txt

  Scenario: Edge check for stale backend metadata constants in mappers
    Tool: Bash
    Steps: grep -R "SERIALIZE_BACKEND\|DESERIALIZE_BACKEND\|SerdeBackendMode" -n serde-processor/src/main/java
    Expected: No active mapper references
    Evidence: .sisyphus/evidence/task-2-no-backend-mapper.txt
  ```

  **Commit**: YES | Message: `refactor(serde-processor): drop backend mode mapping` | Files: serde-processor mapper files

- [x] 3. Simplify `DefaultSerdeRegistry` to metadata-only routing

  **What to do**:
  - Remove backend-mode resolver usage and branching from:
    - `findSerializer(...)`
    - `findDeserializer(...)`
  - Remove `shouldBypassGeneratedSerializer` / `shouldBypassGeneratedDeserializer` and related helper paths.
  - Route generated selection via build-time metadata from introspection (`SOURCEGEN_*_ELIGIBLE` + class keys) and existing generated loader.

  **Must NOT do**:
  - Must not reintroduce new runtime backend policy switches.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: central runtime routing correctness
  - Skills: `[]`
  - Omitted: `[frontend-ui-ux]` — not relevant

  **Parallelization**: Can Parallel: NO | Wave 2 | Blocks: 4,5 | Blocked By: 1,2

  **References**:
  - `serde-support/src/main/java/io/micronaut/serde/support/DefaultSerdeRegistry.java:257-365`
  - `serde-support/src/main/java/io/micronaut/serde/support/runtime/GeneratedSerdeRuntimeLoader.java:36-58`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/SerdeAnnotationVisitor.java:799-813`

  **Acceptance Criteria**:
  - [x] Runtime registry contains no `SerdeBackendMode` branching.
  - [x] No `shouldBypassGenerated*` methods remain.

  **QA Scenarios**:
  ```
  Scenario: Metadata-only dispatch happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenRoutingSpec'
    Expected: Routing assertions pass using SOURCEGEN metadata
    Evidence: .sisyphus/evidence/task-3-routing.txt

  Scenario: Edge check stale bypass heuristics
    Tool: Bash
    Steps: grep -n "shouldBypassGeneratedSerializer\|shouldBypassGeneratedDeserializer\|SerdeBackendModeResolver" serde-support/src/main/java/io/micronaut/serde/support/DefaultSerdeRegistry.java
    Expected: No matches
    Evidence: .sisyphus/evidence/task-3-no-bypass.txt
  ```

  **Commit**: YES | Message: `refactor(serde-support): use build-time sourcegen routing only` | Files: DefaultSerdeRegistry + runtime loader call sites

- [x] 4. Remove runtime backend resolver class and dead references

  **What to do**:
  - Remove `serde-support/.../runtime/SerdeBackendModeResolver.java`.
  - Remove dead imports/fields/constructor init and dependent tests.

  **Must NOT do**:
  - Must not remove `GeneratedSerdeRuntimeLoader` unless fully unused by Task 3 implementation.

  **Recommended Agent Profile**:
  - Category: `quick` — Reason: dead-code cleanup
  - Skills: `[]`
  - Omitted: `[deep]` — logic already handled in Task 3

  **Parallelization**: Can Parallel: YES | Wave 2 | Blocks: 9 | Blocked By: 3

  **References**:
  - `serde-support/src/main/java/io/micronaut/serde/support/runtime/SerdeBackendModeResolver.java`
  - `serde-support/src/main/java/io/micronaut/serde/support/DefaultSerdeRegistry.java:99,124`

  **Acceptance Criteria**:
  - [x] Resolver class removed and no compile references remain.

  **QA Scenarios**:
  ```
  Scenario: Dead-code cleanup happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-support:compileJava
    Expected: Compile success
    Evidence: .sisyphus/evidence/task-4-runtime-cleanup.txt

  Scenario: Stale reference check
    Tool: Bash
    Steps: grep -R "SerdeBackendModeResolver" -n serde-support/src/main/java
    Expected: No matches
    Evidence: .sisyphus/evidence/task-4-no-resolver-refs.txt
  ```

  **Commit**: YES | Message: `refactor(serde-support): remove backend mode resolver runtime path` | Files: serde-support runtime package

- [x] 5. Rework backend-mode tests to metadata-only contract tests

  **What to do**:
  - Remove or rewrite tests tied to runtime backend config:
    - `SerdeBackendModeSpec`
    - `SerdeRuntimeSelectionSpec` backend override cases
    - `SerdeBackendModeConfigurationSpec`
  - Add tests asserting:
    - generated selected when `SOURCEGEN_*_ELIGIBLE=true` and class present
    - fallback when `SOURCEGEN_*_ELIGIBLE=false`
    - serializer/deserializer directional independence from separate eligibility flags.

  **Must NOT do**:
  - Must not weaken assertions to JSON-only; keep class-path routing assertions.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: contract-level test redesign
  - Skills: `[]`
  - Omitted: `[playwright]` — non-UI

  **Parallelization**: Can Parallel: NO | Wave 3 | Blocks: 6,9 | Blocked By: 1,3

  **References**:
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/annotation/SerdeRuntimeSelectionSpec.groovy`
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/annotation/SerdeBackendModeSpec.groovy`
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/annotation/SerdeSourceGenRoutingSpec.groovy`

  **Acceptance Criteria**:
  - [x] No runtime backend-mode override assertions remain.
  - [x] Metadata-only routing tests pass.
  - [x] Missing generated class path has explicit test expecting deterministic `SerdeException`.

  **QA Scenarios**:
  ```
  Scenario: Routing contract happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenRoutingSpec' --tests 'io.micronaut.serde.jackson.annotation.SerdeRuntimeSelectionSpec'
    Expected: Tests pass with metadata-only assertions
    Evidence: .sisyphus/evidence/task-5-routing-tests.txt

  Scenario: Edge check for stale backend-mode test usage
    Tool: Bash
    Steps: grep -R "micronaut\.serde\.backend-mode\|SerdeBackendMode" -n serde-jackson/src/test serde-api/src/test
    Expected: No backend-mode runtime test references
    Evidence: .sisyphus/evidence/task-5-no-backend-tests.txt

  Scenario: Missing generated class failure behavior
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests '*RuntimeSelection*missing*generated*class*'
    Expected: Test passes asserting deterministic SerdeException (fail-fast)
    Evidence: .sisyphus/evidence/task-5-missing-generated-class.txt
  ```

  **Commit**: YES | Message: `test(serde): align routing specs to build-time metadata model` | Files: serde-jackson/src/test, serde-api/src/test

- [x] 6. Regression parity sweep for previously sensitive specs

  **What to do**:
  - Run and fix regressions in sensitive suites affected by routing simplification:
    - `GlobalPropertyStrategySpec`
    - `SerdeImportSpec`
    - `SerdeJsonUnwrappedSpec`
    - `SerdeJsonPropertyOrderSpec`
    - `SerdeJsonIgnoreSpec`
    - BSON parity specs if impacted

  **Must NOT do**:
  - Must not patch tests to hide behavior regressions.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: parity and routing correctness across features
  - Skills: `[]`
  - Omitted: `[artistry]` — conventional debugging path

  **Parallelization**: Can Parallel: YES | Wave 3 | Blocks: 9 | Blocked By: 5

  **References**:
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/GlobalPropertyStrategySpec.groovy`
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/SerdeImportSpec.groovy`
  - `serde-bson/src/test/groovy/io/micronaut/serde/bson/BsonSpec.groovy`

  **Acceptance Criteria**:
  - [x] Targeted parity suites pass.

  **QA Scenarios**:
  ```
  Scenario: Regression happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.GlobalPropertyStrategySpec' --tests 'io.micronaut.serde.jackson.SerdeImportSpec'
    Expected: All tests pass
    Evidence: .sisyphus/evidence/task-6-jackson-parity.txt

  Scenario: BSON edge path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-bson:test --tests 'io.micronaut.serde.bson.BsonSpec'
    Expected: All tests pass
    Evidence: .sisyphus/evidence/task-6-bson-parity.txt
  ```

  **Commit**: YES | Message: `fix(serde): restore parity after metadata-only routing simplification` | Files: runtime/processor as needed + tests

- [x] 7. Add JMH benchmark coverage for SourceGen vs introspection

  **What to do**:
  - Add benchmark(s) under `benchmarks/src/jmh/java/io/micronaut/serde/` to compare generated vs introspection routing on representative fixtures.
  - Include both:
    - throughput mode workload
    - average-time mode workload
  - Reuse fixture/style patterns from existing benchmarks (`@State`, `@Param`, `Blackhole`, setup/teardown lifecycle).

  **Must NOT do**:
  - Must not add CI pass/fail threshold logic.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: benchmark correctness + reproducibility
  - Skills: `[]`
  - Omitted: `[deep]` — not required if fixture scope is constrained

  **Parallelization**: Can Parallel: YES | Wave 4 | Blocks: 8,9 | Blocked By: 6

  **References**:
  - `benchmarks/build.gradle:30-36`
  - `benchmarks/src/jmh/java/io/micronaut/serde/JacksonBenchmark.java`
  - `benchmarks/src/jmh/java/io/micronaut/serde/ComboBenchmark.java`

  **Acceptance Criteria**:
  - [x] New benchmark class compiles and runs via `:benchmarks:jmh`.
  - [x] Output includes generated vs introspection comparison dimensions.

  **QA Scenarios**:
  ```
  Scenario: Benchmark execution happy path
    Tool: Bash
    Steps: ./gradlew :benchmarks:jmh
    Expected: JMH task successful with benchmark results generated
    Evidence: .sisyphus/evidence/task-7-jmh-run.txt

  Scenario: Edge check dead-code safety
    Tool: Bash
    Steps: grep -R "Blackhole\|@Benchmark" -n benchmarks/src/jmh/java/io/micronaut/serde
    Expected: New benchmark uses JMH consumption/benchmark annotations
    Evidence: .sisyphus/evidence/task-7-jmh-structure.txt
  ```

  **Commit**: YES | Message: `perf(benchmarks): add sourcegen vs introspection jmh benchmarks` | Files: benchmarks/src/jmh/java/*, optional benchmark config

- [x] 8. Add benchmark runbook and evidence capture instructions

  **What to do**:
  - Add concise benchmark runbook (where to run, command, interpretation fields, reproducibility knobs).
  - Include guidance for warmup/measurement/fork settings and output artifact location.

  **Must NOT do**:
  - Must not enforce performance pass/fail threshold in CI.

  **Recommended Agent Profile**:
  - Category: `writing` — Reason: technical runbook quality
  - Skills: `[]`
  - Omitted: `[oracle]` — already consulted

  **Parallelization**: Can Parallel: YES | Wave 4 | Blocks: 9 | Blocked By: 7

  **References**:
  - `benchmarks/build.gradle`
  - JMH official guidance (State/Warmup/Measurement/Fork/Blackhole)

  **Acceptance Criteria**:
  - [x] Runbook includes exact command(s), output location, and metric interpretation guidance.

  **QA Scenarios**:
  ```
  Scenario: Runbook happy path
    Tool: Bash
    Steps: ./gradlew :benchmarks:jmh
    Expected: Command in runbook executes successfully as documented
    Evidence: .sisyphus/evidence/task-8-runbook-validation.txt

  Scenario: Edge check missing instructions
    Tool: Bash
    Steps: grep -R "benchmarks:jmh\|warmup\|measurement\|fork" -n .
    Expected: Runbook contains all required knobs and command
    Evidence: .sisyphus/evidence/task-8-runbook-content.txt
  ```

  **Commit**: YES | Message: `docs(benchmarks): add jmh runbook for sourcegen routing validation` | Files: benchmark docs/runbook paths

- [x] 9. Final full verification

  **What to do**:
  - Execute complete test suite and benchmark task.
  - Verify removal checks and no stale backend mode references.

  **Must NOT do**:
  - Must not skip failing tests or narrow suite scope.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: final quality gate
  - Skills: `[]`
  - Omitted: `[quick]` — full-suite scope

  **Parallelization**: Can Parallel: NO | Wave 4 | Blocks: none | Blocked By: 4,6,8

  **References**:
  - `./gradlew test`
  - `./gradlew :benchmarks:jmh`

  **Acceptance Criteria**:
  - [x] Full suite green.
  - [x] Benchmark task green.
  - [x] Grep removal checks green.

  **QA Scenarios**:
  ```
  Scenario: Full verification happy path
    Tool: Bash
    Steps: ./gradlew test && ./gradlew :benchmarks:jmh
    Expected: Both commands succeed
    Evidence: .sisyphus/evidence/task-9-full-verification.txt

  Scenario: Edge check stale backend references
    Tool: Bash
    Steps: grep -R "SerdeBackendMode\|micronaut\.serde\.backend-mode\|SerdeBackendModeResolver" -n serde-api serde-support serde-jackson serde-processor
    Expected: No matches
    Evidence: .sisyphus/evidence/task-9-no-backend-remnants.txt
  ```

  **Commit**: NO | Message: `n/a` | Files: verification only

- [x] 10. Optimize `BeanDeserializerSourceGen` with constants + `createSpecific` child-deserializer caching

  **What to do**:
  - Update `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/beans/BeanDeserializerSourceGen.java` to mirror serializer optimization shape:
    - add static constant fields for property keys and property `Argument` instances (`KEY_*`, `ARGUMENT_*`),
    - add private final cached child deserializer fields (`DESERIALIZER_*`) for non-scalar properties,
    - add public no-arg constructor initializing cache fields to null,
    - add private specialized constructor accepting resolved child deserializers,
    - override `createSpecific(context, type)` (with `throws SerdeException`) to resolve child deserializers once and return specialized generated instance,
    - in hot-path property assignment, use cached deserializer local var first and lazily initialize via existing lookup chain when null (default-constructor compatibility path).
  - Preserve existing `deserializeAndAssignProperty` semantic structure, including `try/catch` wrapper scope and default/null assignment branches.

  **Must NOT do**:
  - Must not static-cache context-specific resolved deserializer instances.
  - Must not change unknown/duplicate/property-path/default/null behavior.
  - Must not remove iterable lookup normalization (`resolveLookupType`).

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: performance refactor with strict behavior parity constraints
  - Skills: `[]`
  - Omitted: `[frontend-ui-ux]` — not relevant

  **Parallelization**: Can Parallel: NO | Wave 5 | Blocks: 11,13 | Blocked By: 9

  **References**:
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/beans/BeanDeserializerSourceGen.java:192-245`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/beans/BeanSerializerSourceGen.java:89-201`
  - Commit motif: `6340a498ff1f02b07a59d88173ab0ff37ef111bf`
  - Commit motif: `88b8676d5160a41c036ecb1337175526e219a701`

  **Acceptance Criteria**:
  - [x] Bean generated deserializer code path no longer performs unconditional per-property lookup chain in the deserialize hot loop.
  - [x] `createSpecific` returns specialized generated bean-deserializer instances with prewired child deserializers.
  - [x] Existing bean deserialization semantics remain unchanged in specialized and default-constructor paths.

  **QA Scenarios**:
  ```
  Scenario: Bean deserializer optimization happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-processor:compileJava :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenBeanSpec'
    Expected: Build successful; bean sourcegen specs pass with optimized generated deserializer
    Evidence: .sisyphus/evidence/task-10-bean-deserializer-opt.txt

  Scenario: Edge check fallback path when not pre-specialized
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-support:test --tests 'io.micronaut.serde.support.deserializers.DeserializeSpec'
    Expected: Default-constructor/non-specialized deserializer path remains correct
    Evidence: .sisyphus/evidence/task-10-bean-fallback-path.txt
  ```

  **Commit**: YES | Message: `perf(sourcegen): optimize bean deserializer specialization and constant reuse` | Files: BeanDeserializerSourceGen + directly related tests

- [x] 11. Optimize `RecordDeserializerSourceGen` with constants + `createSpecific` component-deserializer caching

  **What to do**:
  - Update `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/records/RecordDeserializerSourceGen.java` with parity architecture:
    - static `KEY_*` + `ARGUMENT_*` constants for record components,
    - private final cached `DESERIALIZER_*` fields,
    - public no-arg constructor + private specialized constructor,
    - `createSpecific(context, type)` resolving component deserializers once and returning specialized instance,
    - component decode path using cached field with lazy fallback lookup when null.
  - Preserve canonical constructor ordering, duplicate/unknown handling, default value initialization (`RecordSerdeSourceGenUtils.defaultValueExpression`), and property-path wrapping.

  **Must NOT do**:
  - Must not reorder canonical constructor argument assignment.
  - Must not change default/null initialization behavior for components.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: immutable record construction + strict semantic parity
  - Skills: `[]`
  - Omitted: `[quick]` — correctness risk is non-trivial

  **Parallelization**: Can Parallel: NO | Wave 5 | Blocks: 13,14 | Blocked By: 10

  **References**:
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/records/RecordDeserializerSourceGen.java:186-224`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/records/RecordSerializerSourceGen.java:89-201`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/records/RecordSerdeSourceGenUtils.java:67`

  **Acceptance Criteria**:
  - [x] Record generated deserializer avoids unconditional per-component lookup chain in hot loop.
  - [x] Canonical constructor output and null/default semantics remain unchanged.
  - [x] `SerdeSourceGenRecordSpec` remains green with optimized path.

  **QA Scenarios**:
  ```
  Scenario: Record deserializer optimization happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-processor:compileJava :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenRecordSpec'
    Expected: Build successful; record sourcegen specs pass
    Evidence: .sisyphus/evidence/task-11-record-deserializer-opt.txt

  Scenario: Edge check record default/null behavior
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeJsonIgnoreSpec'
    Expected: Existing null/default record behavior remains intact
    Evidence: .sisyphus/evidence/task-11-record-null-default.txt
  ```

  **Commit**: YES | Message: `perf(sourcegen): optimize record deserializer specialization and constant reuse` | Files: RecordDeserializerSourceGen + related tests

- [x] 12. Optimize `EnumDeserializerSourceGen` for constant reuse and cached string deserializer specialization

  **What to do**:
  - Update `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/enums/EnumDeserializerSourceGen.java` to:
    - declare static string `Argument` constant,
    - add cached string-deserializer field,
    - add constructor + `createSpecific` specialization pattern aligned with enum serializer/deserializer parity,
    - use cached field in deserialize path with lazy fallback when null.
  - Keep enum override mapping behavior unchanged.

  **Must NOT do**:
  - Must not alter enum serialized-value override mapping semantics.
  - Must not introduce runtime behavior differences for unknown enum tokens beyond existing behavior.

  **Recommended Agent Profile**:
  - Category: `quick` — Reason: tightly scoped, single generator file
  - Skills: `[]`
  - Omitted: `[deep]` — no complex object graph handling

  **Parallelization**: Can Parallel: YES | Wave 5 | Blocks: 14 | Blocked By: 10

  **References**:
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/enums/EnumDeserializerSourceGen.java:80-121`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/enums/EnumSerializerSourceGen.java:56-64`

  **Acceptance Criteria**:
  - [x] Enum generated deserializer no longer does unconditional string-deserializer lookup each call.
  - [x] Enum sourcegen tests stay green, including property override mapping cases.

  **QA Scenarios**:
  ```
  Scenario: Enum deserializer optimization happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenEnumSpec'
    Expected: Enum sourcegen tests pass with optimized generated deserializer
    Evidence: .sisyphus/evidence/task-12-enum-deserializer-opt.txt

  Scenario: Edge check enum mapping overrides
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenEnumSpec' --rerun-tasks
    Expected: Alternate serialized-value mapping remains unchanged
    Evidence: .sisyphus/evidence/task-12-enum-override-parity.txt
  ```

  **Commit**: YES | Message: `perf(sourcegen): cache enum string deserializer in generated code` | Files: EnumDeserializerSourceGen + enum sourcegen tests

- [x] 13. Add semantic-lock and generated-shape tests for deserializer optimization parity

  **What to do**:
  - Extend sourcegen deserializer tests to lock behavior for both execution modes:
    - specialized path (`createSpecific` invoked),
    - default-constructor path (lazy fallback path).
  - Add shape assertions (reflection or generated source inspection) to confirm expected optimization structure (constants + cached field presence) without brittle formatting checks.
  - Cover bean + record + enum behavior for unknown/duplicate/null/default/property-path semantics.

  **Must NOT do**:
  - Must not weaken assertions to simple JSON string round-trip only.
  - Must not use brittle exact generated-source formatting assertions.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: cross-spec semantic lock + structural assertions
  - Skills: `[]`
  - Omitted: `[playwright]` — non-UI

  **Parallelization**: Can Parallel: YES | Wave 5 | Blocks: 14,15 | Blocked By: 10,11

  **References**:
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/annotation/SerdeSourceGenBeanSpec.groovy`
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/annotation/SerdeSourceGenRecordSpec.groovy`
  - `serde-jackson/src/test/groovy/io/micronaut/serde/jackson/annotation/SerdeSourceGenEnumSpec.groovy`
  - `serde-support/src/test/groovy/io/micronaut/serde/support/deserializers/DeserializeSpec.groovy`

  **Acceptance Criteria**:
  - [x] Tests explicitly verify specialized vs default-constructor parity for generated deserializers.
  - [x] Unknown/duplicate/null/default/property-path semantics are locked and unchanged.
  - [x] Shape assertions confirm constants/cached fields exist in generated deserializers.

  **QA Scenarios**:
  ```
  Scenario: Semantic lock happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenBeanSpec' --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenRecordSpec' --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenEnumSpec' && ./gradlew :micronaut-serde-support:test --tests 'io.micronaut.serde.support.deserializers.DeserializeSpec'
    Expected: All semantic-lock suites pass
    Evidence: .sisyphus/evidence/task-13-deserializer-semantic-locks.txt

  Scenario: Edge check stale lookup anti-pattern in generated output
    Tool: Bash
    Steps: grep -R "component\\w+\\s*=\\s*\\(.*context\\.findDeserializer\\(.*\\)\\.createSpecific\\(.*\\)\\.deserializeNullable" -n serde-jackson/build/generated/sources serde-support/build/generated/sources
    Expected: No unconditional hot-loop lookup+deserialize chain matches for generated sourcegen deserializers under test fixtures
    Evidence: .sisyphus/evidence/task-13-no-hot-loop-lookup.txt
  ```

  **Commit**: YES | Message: `test(sourcegen): lock deserializer optimization semantics and generated shape` | Files: sourcegen specs + supporting fixtures

- [x] 14. Add guarded scalar decode/default-value fast paths for generated bean/record deserializers

  **What to do**:
  - Add scalar decoder method mapping in bean/record deserializer generators (`decodeString`, `decodeBoolean`, `decodeInt`, etc., plus nullable variants where required) and use direct decode path for scalar properties/components when semantically equivalent.
  - Preserve existing primitive-default assignment behavior and nullable handling.
  - Keep non-scalar and ambiguous cases on existing cached child-deserializer path.

  **Must NOT do**:
  - Must not change `decodeNull` semantics or default assignment behavior.
  - Must not apply fast path where it changes error-path/property-path wrapping behavior.

  **Recommended Agent Profile**:
  - Category: `deep` — Reason: performance optimization with high semantic-safety requirements
  - Skills: `[]`
  - Omitted: `[artistry]` — conservative path required

  **Parallelization**: Can Parallel: NO | Wave 6 | Blocks: 15 | Blocked By: 11,12,13

  **References**:
  - `serde-api/src/main/java/io/micronaut/serde/Decoder.java:91-350`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/beans/BeanSerializerSourceGen.java:301-328`
  - `serde-processor/src/main/java/io/micronaut/serde/processor/sourcegen/records/RecordSerializerSourceGen.java:301-328`

  **Acceptance Criteria**:
  - [x] Scalar bean/record properties/components avoid child-deserializer lookup path when safe.
  - [x] Null/default semantics remain identical to pre-optimization behavior.
  - [x] Property-path wrapped errors remain stable for scalar decode failures.

  **QA Scenarios**:
  ```
  Scenario: Scalar fast path happy path
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenBeanSpec' --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenRecordSpec'
    Expected: Scalar cases pass with generated deserializer fast paths enabled
    Evidence: .sisyphus/evidence/task-14-scalar-fast-path.txt

  Scenario: Edge check null/default/property-path behavior
    Tool: Bash
    Steps: ./gradlew :micronaut-serde-support:test --tests 'io.micronaut.serde.support.deserializers.DeserializeSpec'
    Expected: Null/default/path assertions remain stable
    Evidence: .sisyphus/evidence/task-14-scalar-null-default-path.txt
  ```

  **Commit**: YES | Message: `perf(sourcegen): add guarded scalar decode fast paths for generated deserializers` | Files: bean/record deserializer generators + tests

- [x] 15. Final extension verification (deserializer parity)

  **What to do**:
  - Re-run full relevant test and benchmark gates after tasks 10-14.
  - Re-run stale-reference and anti-pattern checks for deserializer optimization scope.

  **Must NOT do**:
  - Must not skip failing suites.

  **Recommended Agent Profile**:
  - Category: `unspecified-high` — Reason: extension quality gate
  - Skills: `[]`
  - Omitted: `[quick]` — broad verification scope

  **Parallelization**: Can Parallel: NO | Wave 6 | Blocks: none | Blocked By: 13,14

  **References**:
  - `./gradlew test`
  - `./gradlew :benchmarks:jmh`

  **Acceptance Criteria**:
  - [x] Full suite green after deserializer optimization extension.
  - [x] Benchmark task green after extension.
  - [x] No stale deserializer hot-loop lookup anti-patterns in generated source fixtures under test.

  **QA Scenarios**:
  ```
  Scenario: Full verification happy path
    Tool: Bash
    Steps: ./gradlew test && ./gradlew :micronaut-benchmarks:jmh
    Expected: Both commands succeed
    Evidence: .sisyphus/evidence/task-15-full-extension-verification.txt

  Scenario: Edge check anti-pattern remnants
    Tool: Bash
    Steps: grep -R "component\\w+\\s*=\\s*\\(.*context\\.findDeserializer\\(.*\\)\\.createSpecific\\(.*\\)\\.deserializeNullable" -n serde-jackson/build/generated/sources serde-support/build/generated/sources
    Expected: No unconditional per-field/per-component lookup+deserialize anti-pattern remnants for generated sourcegen deserializers
    Evidence: .sisyphus/evidence/task-15-no-hot-loop-remnants.txt
  ```

  **Commit**: NO | Message: `n/a` | Files: verification only

## Final Verification Wave (4 parallel agents, ALL must APPROVE)
- [ ] F1. Plan Compliance Audit — oracle
- [ ] F2. Code Quality Review — unspecified-high
- [ ] F3. Real Manual QA — unspecified-high (+ playwright if UI)
- [ ] F4. Scope Fidelity Check — deep

## Commit Strategy
- C1: Remove backend-mode API/config/mapper metadata
- C2: Simplify registry to metadata-only routing + remove resolver
- C3: Update routing/parity tests to new contract
- C4: Add JMH benchmarks + runbook
- C5: Verification-only (no commit)
- C6: Optimize bean/record/enum generated deserializers with constants + createSpecific caching
- C7: Add deserializer semantic-lock/shape tests + scalar decode fast-path guardrails
- C8: Extension verification-only (no commit)

## Success Criteria
- Runtime backend configurability removed from API/config/runtime paths.
- Routing logic in `DefaultSerdeRegistry` uses build-time sourcegen metadata only.
- `./gradlew test` passes.
- `./gradlew :benchmarks:jmh` passes.
- Benchmark runbook exists and is executable without human guesswork.
- Generated deserializers (bean/record/enum) mirror serializer optimization architecture for constants + `createSpecific` specialization + child-deserializer caching.
- Deserializer behavior remains semantically identical (unknown/duplicate/null/default/property-path) in both specialized and default-constructor execution paths.
