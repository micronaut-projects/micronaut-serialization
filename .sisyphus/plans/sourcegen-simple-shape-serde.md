# SourceGen Build-Time-Only Routing + JMH Validation

## TL;DR
> **Summary**: Re-scope SourceGen work to build-time decisioning only: remove runtime backend-mode configurability and replace registry routing with minimal metadata-driven dispatch.
> **Deliverables**:
> - Remove backend-mode API/config/runtime resolver surfaces
> - Simplify `DefaultSerdeRegistry` to metadata-only generated-vs-introspection selection
> - Preserve serializer/deserializer behavior parity via processor-time eligibility metadata
> - Add JMH benchmarks + runbook to measure throughput/latency impact
> **Effort**: Medium
> **Parallel**: YES - 4 waves
> **Critical Path**: Task 1 → Task 3 → Task 5 → Task 7

## Context
### Original Request
- Reduce `DefaultSerdeRegistry` changes and keep this as a build-time optimization.
- Remove runtime backend selection configuration.
- Remove `shouldBypassGeneratedSerializer`-style runtime heuristics.
- Keep runtime dispatch minimal using build-time metadata.
- Update this plan to include JMH benchmarks for value/throughput validation.

### Interview Summary
- Decided: **remove API + runtime backend config** (not keep/deprecate).
- Decided: **add benchmarks + runbook** (no CI performance threshold gate).
- Default applied: if metadata declares generated serde but generated class cannot be loaded, fail fast with `SerdeException` (no silent fallback).
- Desired runtime routing model:
  - if build-time metadata marks generated serializer/deserializer available, use generated class
  - else fallback to introspection path

### Metis Review (gaps addressed)
- Incorporated guardrails for scope control (no replacement runtime backend switch).
- Added explicit removal-validation checks (grep-based acceptance criteria).
- Added missing negative-path test: generated class metadata present but class load failure behavior.
- Added benchmark execution evidence requirements under existing `benchmarks` module conventions.

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

### Definition of Done (verifiable)
- [ ] `./gradlew test` passes.
- [ ] `./gradlew :benchmarks:jmh` passes.
- [ ] `grep -R "micronaut\.serde\.backend-mode" -n .` returns no matches in active code/tests/docs.
- [ ] `grep -R "SerdeBackendModeResolver\|SerdeBackendMode" -n serde-api serde-support serde-jackson serde-processor` returns no active backend-mode runtime/config references.
- [ ] `DefaultSerdeRegistry` no longer contains `shouldBypassGeneratedSerializer` / `shouldBypassGeneratedDeserializer`.

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

### Dependency Matrix
- Task 1 blocks Task 2/3/5
- Task 2 blocks Task 3
- Task 3 blocks Task 4/5
- Task 4 blocks Task 9
- Task 5 blocks Task 6/9
- Task 6 blocks Task 9
- Task 7 blocks Task 8/9
- Task 8 blocks Task 9

### Agent Dispatch Summary
- Wave 1: unspecified-high (API + metadata surface)
- Wave 2: deep (registry simplification correctness)
- Wave 3: unspecified-high (test parity/regression)
- Wave 4: unspecified-high + quick (bench + runbook + verification)

## TODOs

- [ ] 1. Remove backend-mode API and configuration surfaces

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
  - [ ] Backend-mode symbols are removed from `serde-api` compile surface.
  - [ ] `:micronaut-serde-api:test` passes after test updates.

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

- [ ] 2. Remove backend mapping from processor mappers

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
  - [ ] Processor compiles and existing non-backend mapper behavior remains.

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

- [ ] 3. Simplify `DefaultSerdeRegistry` to metadata-only routing

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
  - [ ] Runtime registry contains no `SerdeBackendMode` branching.
  - [ ] No `shouldBypassGenerated*` methods remain.

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

- [ ] 4. Remove runtime backend resolver class and dead references

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
  - [ ] Resolver class removed and no compile references remain.

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

- [ ] 5. Rework backend-mode tests to metadata-only contract tests

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
  - [ ] No runtime backend-mode override assertions remain.
  - [ ] Metadata-only routing tests pass.
  - [ ] Missing generated class path has explicit test expecting deterministic `SerdeException`.

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

- [ ] 6. Regression parity sweep for previously sensitive specs

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
  - [ ] Targeted parity suites pass.

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

- [ ] 7. Add JMH benchmark coverage for SourceGen vs introspection

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
  - [ ] New benchmark class compiles and runs via `:benchmarks:jmh`.
  - [ ] Output includes generated vs introspection comparison dimensions.

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

- [ ] 8. Add benchmark runbook and evidence capture instructions

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
  - [ ] Runbook includes exact command(s), output location, and metric interpretation guidance.

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

- [ ] 9. Final full verification

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
  - [ ] Full suite green.
  - [ ] Benchmark task green.
  - [ ] Grep removal checks green.

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

## Success Criteria
- Runtime backend configurability removed from API/config/runtime paths.
- Routing logic in `DefaultSerdeRegistry` uses build-time sourcegen metadata only.
- `./gradlew test` passes.
- `./gradlew :benchmarks:jmh` passes.
- Benchmark runbook exists and is executable without human guesswork.
