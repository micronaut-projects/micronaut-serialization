# SourceGen Fast-Path Serde for Simple Shapes

## TL;DR

> **Quick Summary**: Add compile-time generated `Serializer`/`Deserializer` implementations (Micronaut SourceGen) for records, default-constructor beans, and enums, with deterministic fallback to existing introspection runtime paths for complex features.
>
> **Deliverables**:
> - New backend selection controls in annotations + global config rollback switch
> - Processor-time eligibility analysis + generated serde classes for simple shapes
> - Runtime selection gate (generated vs introspection) with strict fallback behavior
> - TDD coverage for selection, fallback, and compatibility
> - Documentation updates in `src/main/docs/guide`
>
> **Estimated Effort**: Large
> **Parallel Execution**: YES – 7 waves
> **Critical Path**: Task 1 → Task 2 → Task 4 → Task 5/6/7 → Task 8 → Task 9 → Task 11

---

## Context

### Original Request
Implement SourceGen-backed serializer/deserializer generation for simple/well-known shapes (record, default-constructor bean, enum), while preserving current introspection-backed runtime behavior for complex Jackson scenarios. Add backward-compat controls (annotation members + rollback) and document feature.

### Interview Summary
**Key Decisions**:
- Default behavior for eligible simple types: **AUTO**.
- Compatibility toggle must exist on all three annotations:
  - `@Serdeable`
  - `@Serdeable.Serializable`
  - `@Serdeable.Deserializable`
- Add **global config rollback switch** in addition to annotation members.
- Test strategy: **TDD**.

**Research Findings**:
- Current processing entrypoint: `serde-processor/.../SerdeAnnotationVisitor.java`.
- Runtime registry dispatch/fallback: `serde-support/.../DefaultSerdeRegistry.java` + `ObjectSerializer`/`ObjectDeserializer`.
- Existing simple/runtime split already exists in `SerBean` and `DeserBean` (`simpleBean`, `recordLikeBean`).
- Existing test already asserts deserializer path choices:
  - `serde-support/src/test/groovy/io/micronaut/serde/support/deserializers/DeserializeSpec.groovy`.
- SourceGen pattern validated from official docs + visitors (`SourceGenerators.findByLanguage(...); sourceGenerator.write(...)`).

### Metis Review (Addressed)
**Gaps raised and resolved in this plan**:
- Explicit backend precedence matrix (annotation vs global) ✅
- Conservative eligibility matrix + hard fallback matrix ✅
- Selection assertions in tests (not only JSON equality) ✅
- Scope lock to phase-1 simple shapes only ✅
- Split-direction behavior (serializer-only/deserializer-only eligibility) ✅

---

## Work Objectives

### Core Objective
Introduce a safe, backward-compatible generated-serde fast path for clearly eligible simple shapes, with zero behavior regressions for existing complex annotation cases.

### Concrete Deliverables
- Annotation/config API for backend selection and rollback
- Processor eligibility analyzer and SourceGen output for:
  - record
  - default-constructor bean
  - enum
- Runtime gate selecting generated or introspection backend deterministically
- Comprehensive TDD suite for routing/fallback/toggles
- Docs in `src/main/docs/guide`

### Definition of Done
- [ ] All targeted tests pass with generated mode enabled and rollback mode forced.
- [ ] `./gradlew check jacocoReport --no-daemon --continue` passes.
- [ ] Existing Jackson complex annotation specs continue passing unchanged.
- [ ] Docs updated with behavior, limits, and rollback guidance.

### Must Have
- Deterministic fallback to introspection for ineligible/complex types.
- Clear precedence and override semantics.
- No human/manual verification required for acceptance.

### Must NOT Have (Guardrails)
- No phase-1 support expansion beyond requested shapes.
- No behavior changes for complex annotation semantics.
- No silent runtime hard-failure in AUTO mode when generated implementation is unavailable.

---

## Verification Strategy (MANDATORY)

> **UNIVERSAL RULE: ZERO HUMAN INTERVENTION**

All tasks are verified via Gradle/test/tool execution only.

### Test Decision
- **Infrastructure exists**: YES
- **Automated tests**: TDD
- **Framework**: Gradle + Spock/TCK test suites

### TDD Pattern (applies per implementation task)
1. **RED**: Add/adjust spec asserting new behavior (selection/fallback/toggle) and confirm failure.
2. **GREEN**: Implement minimal code to satisfy spec.
3. **REFACTOR**: Clean up while keeping tests green.

### Agent-Executed QA Scenarios (global)

Scenario: Generated path selected for eligible record
  Tool: Bash (Gradle test)
  Preconditions: Task 5 implemented, tests added
  Steps:
    1. Run: `./gradlew :serde-support:test --tests '*DeserializeSpec*'`
    2. Run: `./gradlew :serde-jackson:test --tests '*Json*Record*'`
    3. Assert: selection test confirms generated deserializer/serializer class is used
  Expected Result: Eligible record path uses generated backend
  Failure Indicators: Test asserts fallback class or throws missing generated class
  Evidence: `.sisyphus/evidence/task-global-generated-record.txt`

Scenario: Fallback path selected for complex annotations
  Tool: Bash (Gradle test)
  Preconditions: Task 8 implemented
  Steps:
    1. Run: `./gradlew :serde-jackson-tck:test --tests '*JsonUnwrappedSpec*'`
    2. Run: `./gradlew :serde-jackson-tck:test --tests '*JsonSubtypesSpec*'`
    3. Assert: tests pass with routing assertions indicating introspection backend
  Expected Result: Complex cases route to legacy introspection path
  Failure Indicators: Generated backend selected for excluded feature
  Evidence: `.sisyphus/evidence/task-global-fallback-complex.txt`

---

## Execution Strategy

### Parallel Execution Waves

Wave 1 (Start Immediately):
├── Task 1: Backend mode API + config surface

Wave 2 (After Wave 1):
├── Task 2: Annotation mapper propagation
└── Task 3: SourceGen processor wiring

Wave 3 (After Wave 2):
└── Task 4: Eligibility analyzer + fallback reason model

Wave 4 (After Wave 3):
├── Task 5: Record generation
├── Task 6: Default-ctor bean generation
└── Task 7: Enum generation

Wave 5 (After Wave 4):
└── Task 8: Runtime selection gate + precedence rules

Wave 6 (After Wave 5):
├── Task 9: TDD coverage for routing/toggles/fallback
└── Task 10: Documentation updates

Wave 7 (After Wave 6):
└── Task 11: Full verification + regression sweep

Critical Path: 1 → 2 → 4 → 5/6/7 → 8 → 9 → 11

### Dependency Matrix

| Task | Depends On | Blocks | Can Parallelize With |
|------|------------|--------|----------------------|
| 1 | None | 2,3,8 | None |
| 2 | 1 | 4,8 | 3 |
| 3 | 1 | 4 | 2 |
| 4 | 2,3 | 5,6,7 | None |
| 5 | 4 | 8,9 | 6,7 |
| 6 | 4 | 8,9 | 5,7 |
| 7 | 4 | 8,9 | 5,6 |
| 8 | 1,2,5,6,7 | 9,10,11 | None |
| 9 | 8 | 11 | 10 |
| 10 | 8 | 11 | 9 |
| 11 | 9,10 | None | None |

### Agent Dispatch Summary

| Wave | Tasks | Recommended Agents |
|------|-------|--------------------|
| 1 | 1 | `task(category="unspecified-high", load_skills=["git-master"], run_in_background=false)` |
| 2 | 2,3 | Parallel after Wave 1 |
| 3 | 4 | Single focused backend task |
| 4 | 5,6,7 | Parallel generation tasks |
| 5 | 8 | Single integration/selection task |
| 6 | 9,10 | Parallel tests/docs |
| 7 | 11 | Final verification task |

---

## TODOs

- [x] 1. Add backend mode controls in annotation/config API

  **What to do**:
  - Add backend mode member to:
    - `serde-api/src/main/java/io/micronaut/serde/annotation/Serdeable.java`
    - nested `Serializable` and `Deserializable` annotations.
  - Proposed member name: `backend`.
  - Proposed enum values: `AUTO`, `INTROSPECTION`, `GENERATED`.
  - Add metadata constants in `SerdeConfig` for backend mode.
  - Add global config property in `SerdeConfiguration` + `DefaultSerdeConfiguration`:
    - `micronaut.serde.backend-mode` (same enum values).
  - Define precedence contract in code/docs comments.

  **Must NOT do**:
  - Do not remove/rename existing members (`using`, `as`, `validate`, `naming`).

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high` (public API + compatibility semantics)
  - **Skills**: `git-master`
  - **Skills Evaluated but Omitted**: `frontend-ui-ux` (no UI overlap)

  **Parallelization**: NO (Sequential)

  **References**:
  - `serde-api/src/main/java/io/micronaut/serde/annotation/Serdeable.java` — current annotation members and nested type layout.
  - `serde-api/src/main/java/io/micronaut/serde/config/annotation/SerdeConfig.java` — metadata key conventions.
  - `serde-api/src/main/java/io/micronaut/serde/config/SerdeConfiguration.java` — global config prefix and style.
  - `serde-api/src/main/java/io/micronaut/serde/config/DefaultSerdeConfiguration.java` — configuration binding pattern.

  **Acceptance Criteria (TDD)**:
  - [ ] RED: Add annotation metadata test asserting new members are readable.
  - [ ] GREEN: Members compile and metadata constants resolve.
  - [ ] REFACTOR: API javadocs explain precedence and rollback semantics.

  **Agent-Executed QA Scenarios**:
  - Scenario: API compile integrity
    - Tool: Bash
    - Steps: `./gradlew :serde-api:compileJava :serde-api:test`
    - Expected Result: Compilation + tests pass
    - Evidence: `.sisyphus/evidence/task-1-api-compile.txt`
  - Scenario: Negative precedence fixture (conflicting local/global settings)
    - Tool: Bash
    - Steps: run dedicated config precedence spec
    - Expected Result: deterministic precedence assertion passes
    - Evidence: `.sisyphus/evidence/task-1-precedence.txt`

  **Commit**: YES (group with Task 2)

- [x] 2. Propagate backend mode through annotation mappers

  **What to do**:
  - Update:
    - `serde-processor/.../serde/SerdeableMapper.java`
    - `serde-processor/.../serde/SerializableMapper.java`
    - `serde-processor/.../serde/DeserializableMapper.java`
  - Map new annotation member(s) into `SerdeConfig` metadata.

  **Must NOT do**:
  - Do not regress existing mapping for `using/as/validate/naming`.

  **References**:
  - mapper files above
  - `SerdeConfig` metadata constants

  **Acceptance Criteria**:
  - [ ] RED: compile-test expects backend mode metadata present.
  - [ ] GREEN: mapper tests pass and old mapper behavior remains intact.
  - [ ] `./gradlew :serde-processor:test` → PASS.

  **QA Scenarios**:
  - Scenario: Mapper metadata emission
    - Tool: Bash
    - Steps: run processor tests targeting mapper specs
    - Expected Result: backend mode metadata present in generated annotation metadata
    - Evidence: `.sisyphus/evidence/task-2-mapper.txt`
  - Scenario: Negative legacy annotation usage
    - Tool: Bash
    - Steps: run existing specs that use `using/as`
    - Expected Result: unchanged behavior
    - Evidence: `.sisyphus/evidence/task-2-legacy.txt`

  **Commit**: YES (group with Task 1)

- [x] 3. Wire SourceGen into serde-processor

  **What to do**:
  - Add SourceGen dependencies to version catalog and `serde-processor/build.gradle.kts`.
  - Add new TypeElementVisitor class for generation orchestration.
  - Register visitor in:
    - `serde-processor/src/main/resources/META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor`.

  **Must NOT do**:
  - No non-processor modules should gain `micronaut-core-processor` style deps.

  **References**:
  - `serde-processor/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - SourceGen examples:
    - `https://micronaut-projects.github.io/micronaut-sourcegen/latest/guide/`
    - `BuilderAnnotationVisitor` / `DelegateAnnotationVisitor` in micronaut-sourcegen repo.

  **Acceptance Criteria**:
  - [ ] RED: processor compile fails without SourceGen symbols (pre-change baseline)
  - [ ] GREEN: `./gradlew :serde-processor:compileJava` passes with new visitor
  - [ ] Service registration file includes new visitor.

  **QA Scenarios**:
  - Scenario: Processor wiring
    - Tool: Bash
    - Steps: `./gradlew :serde-processor:compileJava`
    - Expected Result: new visitor discovered without service-loader errors
    - Evidence: `.sisyphus/evidence/task-3-wiring.txt`
  - Scenario: Negative missing-language generator guard
    - Tool: Bash
    - Steps: run unit test mocking unsupported language
    - Expected Result: visitor no-ops safely
    - Evidence: `.sisyphus/evidence/task-3-language-guard.txt`

  **Commit**: YES

- [x] 4. Implement eligibility analyzer + fallback reason model

  **What to do**:
  - Add eligibility classifier for phase-1 shapes.
  - Add explicit exclusion matrix for complex features:
    - `SerUnwrapped`, `SerAnyGetter`, `SerAnySetter`, creator complexity, `SerSubtyped`.
  - Add directional capability (serialize, deserialize, both) and reason codes.

  **Must NOT do**:
  - No best-effort generation for uncertain/ambiguous cases.

  **References**:
  - `SerdeAnnotationVisitor` (validation + feature checks)
  - `JsonUnwrappedMapper`, `JsonAnyGetterMapper`, `JsonAnySetterMapper`, `JsonCreatorMapper`, `JsonSubTypesMapper`, `JsonTypeInfoMapper`.
  - `SerBean.isSimpleBean` and `DeserBean.isSimpleBean/isRecordLikeBean` for parity baseline.

  **Acceptance Criteria**:
  - [ ] RED: tests asserting excluded patterns still require fallback.
  - [ ] GREEN: eligibility tests pass for record/default-ctor bean/enum positive fixtures.
  - [ ] Reason codes emitted and asserted in tests.

  **QA Scenarios**:
  - Scenario: Eligible shape matrix test
    - Tool: Bash
    - Steps: run dedicated processor eligibility spec
    - Expected Result: eligible fixtures marked generated
    - Evidence: `.sisyphus/evidence/task-4-eligibility.txt`
  - Scenario: Negative complex feature matrix
    - Tool: Bash
    - Steps: run fixture specs with unwrapped/any/subtype/creator complexities
    - Expected Result: all marked fallback with expected reason code
    - Evidence: `.sisyphus/evidence/task-4-fallback-matrix.txt`

  **Commit**: YES

- [ ] 5. Generate serializer/deserializer for records

  **What to do**:
  - Generate direct serializer using record component accessors.
  - Generate direct deserializer buffering component values then calling canonical constructor.

  **Must NOT do**:
  - Do not support record edge features excluded by matrix in phase 1.

  **References**:
  - `Deserializer.java`, `Serializer.java` API contracts
  - `SimpleRecordLikeObjectDeserializer` behavior baseline
  - SourceGen `ClassDef`/`MethodDef` generation pattern

  **Acceptance Criteria**:
  - [ ] RED: record selection test expects generated backend.
  - [ ] GREEN: round-trip tests pass for record fixtures.
  - [ ] Negative fixture with excluded annotation falls back.

  **QA Scenarios**:
  - Scenario: Record happy path
    - Tool: Bash
    - Steps: run record-focused compile/runtime specs
    - Expected Result: generated backend selected + payload parity
    - Evidence: `.sisyphus/evidence/task-5-record-happy.txt`
  - Scenario: Record negative (excluded annotation)
    - Tool: Bash
    - Steps: run record fixture with `JsonUnwrapped`/creator complexity
    - Expected Result: fallback backend selected
    - Evidence: `.sisyphus/evidence/task-5-record-negative.txt`

  **Commit**: YES (group with 6/7 possible)

- [ ] 6. Generate serializer/deserializer for default-constructor beans

  **What to do**:
  - Serializer: direct getter access.
  - Deserializer: instantiate default ctor, then setter/property assignment.

  **Must NOT do**:
  - No support for mixed constructor+setter complex cases in phase 1.

  **References**:
  - `SimpleObjectDeserializer`, `SpecificObjectDeserializer` distinction
  - `DeserializeSpec` baseline assertions

  **Acceptance Criteria**:
  - [ ] RED: bean fixture expects generated backend for simple case.
  - [ ] GREEN: round-trip parity tests pass.
  - [ ] Complex mixed fixture remains fallback.

  **QA Scenarios**:
  - Scenario: Bean happy path
    - Tool: Bash
    - Steps: run simple setter-bean spec
    - Expected Result: generated selected and values match
    - Evidence: `.sisyphus/evidence/task-6-bean-happy.txt`
  - Scenario: Bean negative mixed ctor+setter
    - Tool: Bash
    - Steps: run `MyMixSetterConstructorPropertiesBean`-style fixture
    - Expected Result: fallback to specific/introspection path
    - Evidence: `.sisyphus/evidence/task-6-bean-negative.txt`

  **Commit**: YES

- [ ] 7. Generate serializer/deserializer for enums

  **What to do**:
  - Generate direct name/property-based enum serde for simple eligible enum cases.
  - Fallback to current `EnumSerde` path when excluded by matrix (`JsonValue`, creator complexity, etc.).

  **Must NOT do**:
  - No expansion to all advanced enum customization in phase 1.

  **References**:
  - `serde-support/.../serdes/EnumSerde.java`
  - existing enum tests (`SerdeJsonEnumSpec`, `JsonEnumSpec`)

  **Acceptance Criteria**:
  - [ ] RED: enum simple fixture expects generated backend.
  - [ ] GREEN: serialization/deserialization parity with existing behavior.
  - [ ] Excluded enum features fallback correctly.

  **QA Scenarios**:
  - Scenario: Enum happy path
    - Tool: Bash
    - Steps: run enum spec subset
    - Expected Result: generated selected + case behavior preserved
    - Evidence: `.sisyphus/evidence/task-7-enum-happy.txt`
  - Scenario: Enum negative custom creator/value
    - Tool: Bash
    - Steps: run fixtures with enum creator/value annotation
    - Expected Result: fallback path
    - Evidence: `.sisyphus/evidence/task-7-enum-negative.txt`

  **Commit**: YES

- [ ] 8. Add runtime selection gate + precedence rules

  **What to do**:
  - Integrate generated-vs-introspection selection in registry/object path.
  - Respect precedence:
    1) per-direction annotation member
    2) `@Serdeable` member
    3) global config
    4) default AUTO
  - Support directional selection independently for serializer/deserializer.
  - AUTO behavior: use generated if eligible + available; else fallback.

  **Must NOT do**:
  - No ambiguity in selection when both backends exist.

  **References**:
  - `DefaultSerdeRegistry.findSerializer/findDeserializer`
  - `ObjectSerializer.createSpecificInternal`
  - `ObjectDeserializer.createSpecific` / `findDeserializer`

  **Acceptance Criteria**:
  - [ ] RED: precedence tests fail before implementation.
  - [ ] GREEN: precedence + directional split tests pass.
  - [ ] Global rollback switch forces old behavior.

  **QA Scenarios**:
  - Scenario: Global rollback
    - Tool: Bash
    - Steps: run tests with config forcing introspection mode
    - Expected Result: generated never selected
    - Evidence: `.sisyphus/evidence/task-8-global-rollback.txt`
  - Scenario: Directional override
    - Tool: Bash
    - Steps: run fixture with serialization generated, deserialization introspection
    - Expected Result: split behavior matches declared precedence
    - Evidence: `.sisyphus/evidence/task-8-directional.txt`

  **Commit**: YES

- [ ] 9. Add/extend TDD coverage for selection + fallback + compatibility

  **What to do**:
  - Add tests proving backend selection explicitly.
  - Extend existing specs/TCK fixtures for:
    - simple record/bean/enum generation
    - fallback for unwrapped/any/subtypes/creator complexity
    - toggle precedence and global rollback.

  **Must NOT do**:
  - No tests that only check JSON equality while ignoring backend routing.

  **References**:
  - `serde-support/src/test/.../DeserializeSpec.groovy`
  - `serde-jackson-tck/src/main/groovy/io/micronaut/serde/jackson/*`
  - `test-suite-tck-jackson-databind/src/test/groovy/...`

  **Acceptance Criteria**:
  - [ ] New tests fail first (RED), then pass after implementation.
  - [ ] Targeted module tests pass:
    - `:serde-support:test`
    - `:serde-jackson:test`
    - `:serde-jackson-tck:test`

  **QA Scenarios**:
  - Scenario: Backend selection assertions
    - Tool: Bash
    - Steps: execute targeted specs with backend class assertions
    - Expected Result: selection assertions pass
    - Evidence: `.sisyphus/evidence/task-9-selection.txt`
  - Scenario: Regression negative matrix
    - Tool: Bash
    - Steps: execute excluded-feature suites
    - Expected Result: no behavior regressions
    - Evidence: `.sisyphus/evidence/task-9-regression.txt`

  **Commit**: YES

- [ ] 10. Document feature and rollback controls

  **What to do**:
  - Add guide section for generated simple-shape backend.
  - Update TOC and cross-links from quick start / custom serdes sections.
  - Document:
    - eligibility matrix
    - fallback matrix
    - annotation members and precedence
    - global rollback property
    - known exclusions for phase 1.

  **Must NOT do**:
  - No vague claims like “works for all Jackson features”.

  **References**:
  - `src/main/docs/guide/toc.yml`
  - `src/main/docs/guide/quickStart.adoc`
  - `src/main/docs/guide/serdes.adoc`
  - `src/main/docs/guide/jacksonAnnotations.adoc`

  **Acceptance Criteria**:
  - [ ] New doc section linked in TOC.
  - [ ] Property names and precedence described with concrete examples.
  - [ ] Build docs/check task passes.

  **QA Scenarios**:
  - Scenario: Docs build integrity
    - Tool: Bash
    - Steps: `./gradlew docs`
    - Expected Result: no broken links/includes
    - Evidence: `.sisyphus/evidence/task-10-docs.txt`
  - Scenario: Negative doc assertion
    - Tool: Bash
    - Steps: grep for old-only behavior statement without generated-mode mention
    - Expected Result: updated docs reflect new default AUTO mode
    - Evidence: `.sisyphus/evidence/task-10-content.txt`

  **Commit**: YES

- [ ] 11. Final verification and release-readiness sweep

  **What to do**:
  - Run full impacted test/build matrix.
  - Confirm rollback mode and AUTO mode both green.
  - Confirm no checkstyle/format regressions.

  **Acceptance Criteria**:
  - [ ] `./gradlew :serde-api:test :serde-processor:test :serde-support:test :serde-jackson:test :serde-jackson-tck:test`
  - [ ] `./gradlew check jacocoReport --no-daemon --continue`
  - [ ] No failing selection/fallback specs in either mode

  **QA Scenarios**:
  - Scenario: AUTO mode full run
    - Tool: Bash
    - Steps: run full matrix with default settings
    - Expected Result: all pass
    - Evidence: `.sisyphus/evidence/task-11-auto.txt`
  - Scenario: Rollback mode full run
    - Tool: Bash
    - Steps: run targeted selection suites with global rollback property forced
    - Expected Result: all pass and generated path disabled
    - Evidence: `.sisyphus/evidence/task-11-rollback.txt`

  **Commit**: NO (verification-only)

---

## Commit Strategy

| After Task | Message | Files | Verification |
|------------|---------|-------|--------------|
| 1-2 | `feat(serde-api): add backend mode controls and mapper metadata` | serde-api + processor mapper files | `:serde-api:test :serde-processor:test` |
| 3-4 | `feat(serde-processor): wire sourcegen and add simple-shape eligibility analyzer` | processor build + visitor + analyzer | `:serde-processor:test` |
| 5-7 | `feat(serde-processor): generate serde implementations for records beans and enums` | processor generation sources | targeted generation tests |
| 8 | `feat(serde-support): add deterministic generated-vs-introspection selection gate` | serde-support runtime selection files | `:serde-support:test` |
| 9 | `test(serde): add routing fallback and toggle precedence coverage` | support/jackson/tck tests | module tests |
| 10 | `docs(guide): document sourcegen simple-shape backend and rollback controls` | src/main/docs/guide/* | docs check/build |

---

## Success Criteria

### Verification Commands
```bash
./gradlew :serde-api:test :serde-processor:test :serde-support:test :serde-jackson:test :serde-jackson-tck:test
./gradlew check jacocoReport --no-daemon --continue
```

### Final Checklist
- [ ] All Must Have requirements satisfied
- [ ] All Must NOT Have guardrails preserved
- [ ] AUTO mode and rollback mode validated
- [ ] Fallback matrix cases proven via tests
- [ ] Docs updated and linked in guide TOC
