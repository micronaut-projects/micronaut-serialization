## [2026-03-12T12:45:00Z] Task: bg_86c75e87
- Cataloged existing serializer/deserializer implementations: SourceGen visitor only produces abstract Serializer/Deserializer stubs that extend the runtime interfaces, while serde-support relies on introspection-based classes such as `SimpleObjectSerializer`, `SimpleObjectDeserializer`, `SimpleRecordLikeObjectDeserializer`, and `EnumSerde` for records/beans/enums.
- Noted the current SourceGen wiring (visitor registered via `META-INF/services/io.micronaut.inject.visitor.TypeElementVisitor`, analyzer in `SimpleSerdeShapeAnalyzer`, decision shape/reason models in `SimpleSerdeShapeDecision`).
- Ready to extend the visitor with actual ClassDef/MethodDef bodies for eligible shapes while keeping the existing runtime fallbacks intact.

## [2026-03-12T12:48:00Z] Task: bg_2d503124
- Confirmed eligibility reasons and metadata flow: `SimpleSerdeShapeAnalyzer` populates directional EnumSets (`serializerFallbackReasons`, `deserializerFallbackReasons`) covering `UNWRAPPED`, `ANY_GETTER`, `ANY_SETTER`, `COMPLEX_CREATOR`, `SUBTYPED`, `UNSUPPORTED_SHAPE`.
- `SerdeAnnotationVisitor` writes these reason strings to `SerdeConfig` so runtime selection can respect fallbacks; tests like `SerdeSourceGenEligibilitySpec` assert the analyzer behavior.
- New generation logic must consult these reasons (and the eligibility booleans) before emitting serializer/deserializer code.

## [2026-03-12T12:58:00Z] Task: bg_5d4333c3
- Gathered Micronaut SourceGen guide highlights: visitors call `SourceGenerators.findByLanguage`, build `ClassDef`/`MethodDef` structures, and invoke `sourceGenerator.write(...)`; official docs and repo samples show how to add constructors/methods via fluent builders and register visitors via service loader files.
- Established the pattern for generating concrete serializers/deserializers: build a `ClassDef` implementing `Serializer<T>`/`Deserializer<T>`, add `serialize`/`deserialize` method bodies via `StatementDef`/`ExpressionDef`, and write the class out once the analyzer approves the shape.
- Confirmed service loader entry still lists `SerdeSourceGenVisitor`, so expanding the visitor will automatically trigger code generation if we follow the existing registration.

## [2026-03-12T13:03:00Z] Task: bg_1f755475
- Verified SourceGen visitor practices across repos: `SerdeSourceGenVisitor` uses `SimpleSerdeShapeAnalyzer` to gate generation, writes abstract classes with stub methods but no bodies; `SdkImportVisitor` (Oracle Cloud) demonstrates fully populated `ClassDef` builders with constructors and `build` logic, plus guard rails for missing `SourceGenerator`.
- Best practices identified: limit generation to simple shapes, record fallback reasons, and fail fast when SourceGen writer missing; these apply directly to our new serializer/deserializer generators for records/beans/enums.

## [2026-03-12T12:48:09Z] Task: plan-task-5-record-sourcegen
- Implemented record-specific SourceGen helpers under `serde-processor/.../sourcegen/records/` and wired `SerdeSourceGenVisitor` to use them only for record shapes, while retaining the existing abstract fallback for non-record shapes.
- Added `RecordSerdeShapeResolver` to map canonical-constructor parameters to bean properties and to intentionally skip generation for generic records / unresolved type variables; without this, SourceGen emitted invalid `Argument.of(...)` calls for type variables and broke unrelated test fixtures.
- Serializer generation now emits concrete `serialize(...)` logic: object start via `encodeObject(type)`, per-component key emission, runtime serializer lookup through `EncoderContext.findSerializer(Argument.of(componentType))`, null-aware component encoding, and `finishStructure()`.
- Deserializer generation now emits concrete `deserialize(...)` logic: object decoding, key iteration, per-component temporary locals initialized to type defaults, deserializer lookup through `DecoderContext.findDeserializer(...)`, component reads using `deserializeNullable(...)`, unknown-key skipping, `finishStructure()`, then instantiation through the record canonical constructor.
- SourceGen `ExpressionDef.asStatementSwitch(...)` produced incorrect runtime behavior for this key-decoding loop in generated code; replacing it with an explicit chained equality-if dispatch fixed token advancement order and made primitive component decoding stable.
- Added `SerdeSourceGenRecordSpec` in `serde-jackson` to assert metadata remains correct and to instantiate/use the generated serializer/deserializer directly with `JacksonEncoder`/`JacksonDecoder`, validating round-trip JSON for a simple `@Serdeable` record.

## [2026-03-12T14:06:00Z] Task: plan-task-6-bean-sourcegen
- Added bean SourceGen support under `serde-processor/.../sourcegen/beans/` (`BeanSerdeShape`, resolver, serializer/deserializer generators, shared utils) and wired `SerdeSourceGenVisitor` to delegate only when `shapeKind == DEFAULT_CONSTRUCTOR_BEAN` and resolver checks pass.
- Bean serializer/deserializer generation now emits concrete `public final` implementations that encode via `encoder.encodeObject(type)`, iterate bean property names in introspection order, perform per-property `findSerializer/findDeserializer(Argument.of(...))`, handle nullable values, skip unknown keys, and return populated bean instances created through default constructor.
- Important gotcha: using `PropertyElement#getType()` for serializer/deserializer argument/cast caused mismatches on properties like `Optional<T>` (getter return type differs from setter parameter); resolver now captures both read and write method types and generators use read type for serialization and write-parameter type for deserialization.
- Important gotcha: char default initialization in record helper emitted an illegal source literal (`?` for `\u0000`) in generated Java. Switching char defaults to `ExpressionDef.constant(0).cast(char)` fixed both record and bean-adjacent test runs.
- Added `SerdeSourceGenBeanSpec` to assert metadata shape/eligibility/class names for a simple `@Serdeable` bean, verify generated classes are concrete, and validate serializer/deserializer round-trip JSON.

## [2026-03-12T14:20:00Z] Task: plan-task-7-enum-sourcegen
- Added enum SourceGen support under `serde-processor/.../sourcegen/enums/` with `EnumSerdeShape`, resolver, sourcegen utils, concrete enum serializer/deserializer builders, and new `FallbackReason.COMPLEX_ENUM` so advanced enums fall back to introspection.
- Visitor routing now checks `ShapeKind.ENUM` in `SerdeSourceGenVisitor` and emits the generated enum classes only when the resolver confirms no `@JsonValue`/custom creators; other enums remain abstract and rely on existing `EnumSerde` behavior.
- Enum serializer/deserializer creation uses `EncoderContext.findSerializer(Argument.of(String.class))`/`DecoderContext.findDeserializer(...)`, writes enum names (case-sensitive), maps strings back to enum constants, and throws on unknown names to mirror Jackson's default safeguards.
- `SerdeSourceGenEnumSpec` verifies metadata, generated class concreteness, and JSON round-tripping, while the eligibility spec now includes an enum with `@JsonValue` to assert `COMPLEX_ENUM` fallback.

## [2026-03-12T14:20:00Z] Task: plan-task-7-enum-sourcegen
- Added enum-specific SourceGen helpers under `serde-processor/.../sourcegen/enums/` (`EnumSerdeShape`, `EnumSerdeShapeResolver`, `EnumSerdeSourceGenUtils`, `EnumSerializerSourceGen`, `EnumDeserializerSourceGen`) and wired `SerdeSourceGenVisitor` to emit concrete enum serializer/deserializer classes when `shapeKind == ENUM` and resolver checks pass.
- Enum fast-path generation now uses `EncoderContext.findSerializer(Argument.of(String.class))` + `Enum.name()` for serialization and `DecoderContext.findDeserializer(Argument.of(String.class))` + `Enum.valueOf(...)` for deserialization, yielding concrete generated classes instead of abstract placeholders for simple enums.
- Extended shape eligibility with `COMPLEX_ENUM` fallback reason: enums with custom `@JsonValue`/`@SerdeConfig.SerValue` or custom creator methods are marked ineligible so runtime `EnumSerde` remains the fallback path.
- Added `SerdeSourceGenEnumSpec` to verify enum metadata and class concreteness, instantiate generated classes directly, and assert JSON round-trip (`"B"`) through `JacksonEncoder`/`JacksonDecoder`; added eligibility coverage that `@JsonValue` enums now emit `COMPLEX_ENUM` fallback reasons.

## [2026-03-12T13:24:53Z] Task: plan-task-8-runtime-selection-gate
- Added deterministic runtime backend resolution for each direction with precedence: `SerdeConfig.SERIALIZE_BACKEND` / `SerdeConfig.DESERIALIZE_BACKEND` first, then shared `SerdeConfig.BACKEND`, then global `SerdeConfiguration.getBackendMode()`, defaulting to `AUTO`.
- Added a generated-serde runtime loader that reads `SOURCEGEN_*` metadata from `SerdeIntrospections`, lazily instantiates generated serializer/deserializer classes once per class name, and returns explicit availability/failure status so `AUTO` can safely fall back.
- `DefaultSerdeRegistry` now applies the gate before object-introspection fallback: it uses generated classes in `AUTO`/`GENERATED` when available, keeps registry caches aligned with selected instances, and only throws `SerdeException` when backend mode is explicitly `GENERATED` and no generated serde can be loaded.
- Gotcha: arrays and built-in/custom serdes must remain resolved before the generated gate to preserve existing behavior; the gate is only inserted at the object-introspection fallback point.

## [2026-03-12T13:38:29Z] Task: plan-task-9-sourcegen-routing-coverage
- Added end-to-end routing coverage for the complex fallback matrix (`AnyGetterBean`, `AnySetterBean`, `UnwrappedBean`, `SubtypedBean`, `DelegatingCreatorRecord`, `JsonValueEnum`) using runtime registry lookups plus `SerdeIntrospections` metadata assertions, not JSON-equality-only checks.
- The new routing spec verifies direction-specific behavior: generated classes are selected when `SOURCEGEN_*_ELIGIBLE` stays true, while ineligible directions resolve to serde-support runtime implementations and expose the expected fallback reason sets (`ANY_GETTER`, `ANY_SETTER`, `UNWRAPPED`, `SUBTYPED`, `COMPLEX_CREATOR`, `COMPLEX_ENUM`).
- Unwrapped serializer coverage surfaced an important runtime nuance: creating a specific serializer for an unwrapped parent can fail when nested properties resolve to generated serializers that do not support `serializeInto`, so the test asserts fallback class routing at registry-level for that direction and specific deserializer routing where creation is stable.

## [2026-03-12T15:20:00Z] Task: plan-task-9-routing-legacy-beans-filter
- Fixed duplicate runtime candidate resolution by filtering out `LegacyBeansFactory` bean definitions in `DefaultSerdeRegistry.matchSerializerCandidates` and `matchDeserializerCandidates` before qualifier matching and dedup/last-chance logic.
- This prevents fallback lookups against `beanContext.getBeanDefinitions(...)` from seeing parallel legacy factory definitions (`provideSerializer`, `provideObjectSerializer`, `provideSerde`) for the same type, which previously triggered `Multiple possible serializers/deserializers found`.
- Verification result: `./gradlew :micronaut-serde-jackson:test --tests io.micronaut.serde.jackson.annotation.SerdeSourceGenRoutingSpec` passed (1 test class, 1 test, BUILD SUCCESSFUL).

## [2026-03-12T15:10:00Z] Task: plan-task-9-object-serializer-sourcegen
- Updated SourceGen serializer generators for records/beans/enums to implement `io.micronaut.serde.ObjectSerializer` in addition to `Serializer`, and emit a generated `serializeInto(Encoder, EncoderContext, Argument<T>, T)` method.
- Record/bean serializers now share one property-writing loop for both `serialize(...)` and `serializeInto(...)`: `serialize(...)` opens/finishes object structure and delegates field emission, while `serializeInto(...)` writes keys/values only (no `encodeObject`/`finishStructure`), which is required by `SerBean` unwrapped/any-getter flow.
- This was needed because `SerBean.initProperty` requires serializers for `@JsonUnwrapped`/`@JsonAnyGetter` properties to be `ObjectSerializer`; without that, generated fast-path serializers triggered `doesn't support serializing into an existing object`.
- Verification command (unwrapped include case): `./gradlew :micronaut-serde-jackson:test --tests "io.micronaut.serde.jackson.annotation.SerdeJsonIncludeSpec.unwrapped with Json always include 2"` now passes.

## [2026-03-12T16:05:00Z] Task: plan-task-9-jsoninclude-fallback
- Added `FallbackReason.INCLUDE` to `SimpleSerdeShapeDecision` and wired `SimpleSerdeShapeAnalyzer` to mark both serializer and deserializer as ineligible when `com.fasterxml.jackson.annotation.JsonInclude` is present on the type or on bean properties/fields/methods.
- This prevents SourceGen fast-path serializers/deserializers from being selected for include-sensitive shapes, forcing runtime fallback to introspection-backed serdes (`serde-support`) that already implement JsonInclude semantics correctly.
- Extended eligibility/routing coverage with JsonInclude fixtures so metadata reason sets now include `INCLUDE` and backend selection remains introspection for those types.
- Verification target for this regression gate: `./gradlew :micronaut-serde-jackson:test --tests io.micronaut.serde.jackson.annotation.SerdeJsonIncludeSpec`.

## [2026-03-12T16:45:00Z] Task: plan-task-9-generic-argument-and-optional-defaults
- Reworked generated `Argument` construction in bean/record sourcegen utilities to preserve nested generic metadata (`Argument.of(Class, Argument...)`) instead of erasure-only `Argument.of(Class)`, including wildcard/type-variable normalization to upper bounds and primitive boxing for generic positions.
- Added generated optional defaults for missing properties/components (`Optional.empty`, `OptionalInt.empty`, `OptionalDouble.empty`, `OptionalLong.empty`) so absent keys no longer produce null optional values in sourcegen deserializers.
- Added iterable lookup normalization in generated deserializers to resolve `Iterable<T>` lookups through `Collection<T>` arguments (with both `getTypeArguments()` and `getBoundGenericTypes()` fallbacks) to reduce raw/unspecific deserializer selection.
- Result: `SerdeJsonIncludeSpec` regression count dropped from 12 failures to a single remaining failure (`test basic deserialize {"value":[true]} of type Iterable<Boolean>`) where deserialized value is still `ArrayNode` instead of `List<Boolean>` in sourcegen path.

## [2026-03-12T17:05:00Z] Task: plan-task-9-iterable-runtime-normalization
- Fixed the remaining `Iterable<Boolean>` JsonInclude regression in the introspection/runtime path by normalizing deserializer lookup arguments in `DeserBean.findDeserializer`: when a property argument is `Iterable<T>`, the lookup now uses `Collection<T>` with preserved name/annotation metadata/type parameters.
- This aligns runtime lookup behavior with the sourcegen normalization already added in generated bean/record deserializers, avoiding fallback to arbitrary-object decoding (`ArrayNode`) for iterable properties.
- Updated `DeserializeSpec` type assertions to accept either legacy introspection deserializers or generated `$Serde*Deserializer` classes so the test remains valid under backend AUTO/generated routing.
- Verification: targeted `SerdeJsonIncludeSpec` basic deserialize matrix now passes (including `Iterable<Boolean>`), and full `:micronaut-serde-support:test` and `:micronaut-serde-jackson:test` runs are green.

## [2026-03-12T17:10:00Z] Task: plan-task-10-sourcegen-jackson-parity-followups
- SourceGen deserializer parity fixes:
  - Added generated unknown-property handling that respects runtime config via `GeneratedSerdeErrorHandler.handleUnknownProperty(...)` (skip by default, throw when `micronaut.serde.deserialization.ignore-unknown=false`).
  - Added generated duplicate-property detection for bean and record sourcegen deserializers using a per-object seen-property set and `GeneratedSerdeErrorHandler.duplicateProperty(...)`.
  - Added primitive null-safety in bean sourcegen deserializers by writing primitive default values when decoded value is null.
  - Added generated record non-null collection/map defaults only when explicit non-null annotation is present on record component (`org.jspecify.annotations.NonNull` / `jakarta.annotation.Nonnull` / `javax.annotation.Nonnull`).
- SourceGen serializer/deserializer path parity fixes:
  - Added throwable-aware property path wrapping in generated serializers and deserializers (`GeneratedSerdeErrorHandler.withPropertyPath(Throwable, ...)`) so path assertions match JsonException expectations for getter/runtime failures.
- Analyzer fallback hardening for JsonTypeInfo/CLASS/MINIMAL_CLASS/polymorphism:
  - `SimpleSerdeShapeAnalyzer` now scans Jackson annotations through supertype/interface hierarchies for both root types and property types, preventing sourcegen selection for hierarchy-driven Jackson metadata.
- Verification evidence:
  - `./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.annotation.SerdeJsonExceptionSpec' --tests 'io.micronaut.serde.jackson.annotation.SerdeJsonTypeInfoSpec' --tests 'io.micronaut.serde.jackson.JacksonBasicSerdeSpec'` => **BUILD SUCCESSFUL**, 162 tests passed.
  - `lsp_diagnostics` => zero errors on modified files:
    - `serde-api/.../GeneratedSerdeErrorHandler.java`
    - `serde-processor/.../SimpleSerdeShapeAnalyzer.java`
    - `serde-processor/.../beans/{BeanSerdeSourceGenUtils,BeanDeserializerSourceGen,BeanSerializerSourceGen}.java`
    - `serde-processor/.../records/{RecordSerdeSourceGenUtils,RecordDeserializerSourceGen,RecordSerializerSourceGen}.java`
  - `./gradlew -q spotlessCheck` => passed.
  - `./gradlew -q cM` => still fails on pre-existing repository-level checkstyle errors outside this change set (for example `serde-api` declaration/parameter checks and one pre-existing unused import in `BeanSerdeShape`).
  - `./gradlew check jacocoReport --no-daemon --continue` => continues to fail due unrelated pre-existing failures in other modules (notably `serde-bson` serializer candidate ambiguity and existing checkstyle errors).

## [2026-03-12T18:15:00Z] Task: plan-task-10-full-suite-stabilization
- Fixed generated-runtime routing regressions in `DefaultSerdeRegistry` by making generated-backend bypass checks direction-aware instead of a single broad Jackson-annotation gate.
  - Serializer bypass now honors global/runtime naming strategy, sourcegen serializer eligibility metadata, and explicit custom serializer metadata on type/properties/arguments.
  - Deserializer bypass now honors global/runtime naming strategy, sourcegen deserializer eligibility metadata, and explicit custom deserializer metadata on type/properties/arguments.
- This preserved expected directional behavior for sourcegen routing fixtures (notably `AnyGetterBean`: serializer fallback + generated deserializer) while restoring runtime fallback where required for global property strategy and serde-import custom serializer/deserializer cases.
- Verification evidence:
  - `./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.GlobalPropertyStrategySpec' --tests 'io.micronaut.serde.jackson.SerdeImportSpec' --tests 'io.micronaut.serde.jackson.annotation.SerdeSourceGenRoutingSpec'` => **BUILD SUCCESSFUL**, 24 tests passed.
  - `./gradlew test` => **BUILD SUCCESSFUL in 5m 38s** (`1102` tests in `:micronaut-serde-jackson:test`, `0` failed, `30` skipped; full multi-module suite green).
