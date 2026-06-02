# JSON-P and JSON-B Jakarta Spec Support Plan

## Summary

Add first-class Jakarta JSON-P and JSON-B support to Micronaut Serialization without changing the existing Parsson-backed `serde-jsonp` module.

- Add `serde-jsonp-impl`: a Micronaut Serialization JSON-P provider with no Parsson dependency and no reflection.
- Add `serde-jsonb`: a JSON-B runtime provider built primarily on Micronaut Serialization and generated serializers, with reflection only as an isolated last-resort fallback required by JSON-B/TCK behavior.
- Add standalone JSON-P and JSON-B TCK modules with evidence generation, following the recent JAX-RS/Validation TCK pattern.
- Update docs to clearly distinguish the existing Parsson bridge from the new Micronaut-native JSON-P implementation.

## Key Changes

### Modules And Build

- Add modules:
  - `serde-jsonp-impl`
  - `serde-jsonb`
  - `tests:jsonp-tck`
  - `tests:jsonb-tck`
- Keep existing `serde-jsonp` unchanged as the Parsson-backed stream bridge.
- Add BOM/version catalog entries for the standalone Jakarta TCK artifacts:
  - JSON-P standalone TCK aligned with `jakarta.json-api`
  - JSON-B standalone TCK aligned with `jakarta.json.bind-api`
- Add dependency guards:
  - `serde-jsonp-impl` must not bring in Eclipse Parsson.
  - `serde-jsonb` must not require Yasson.
- Register service providers:
  - `META-INF/services/jakarta.json.spi.JsonProvider`
  - `META-INF/services/jakarta.json.bind.spi.JsonbProvider`

### JSON-P Implementation

- Implement `jakarta.json.spi.JsonProvider` in `serde-jsonp-impl`.
- Back parser/generator behavior with the Jackson core parser/generator stack already used by `serde-jackson`.
- Implement the full JSON-P API surface:
  - `JsonParser`, `JsonGenerator`
  - parser/generator factories
  - `JsonReader`, `JsonWriter`
  - reader/writer factories
  - `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonValue`
  - object/array builders and builder factory
  - `JsonPointer`
  - `JsonPatch`, `JsonPatchBuilder`
  - `JsonMergePatch`
  - diff and merge-diff creation
- Support JSON-P config behavior, including pretty printing and duplicate-key handling through `JsonConfig.KEY_STRATEGY`.
- Enforce the module constraint: no reflection usage in `serde-jsonp-impl`.

### JSON-B Implementation

- Add `serde-jsonb` implementing:
  - `JsonbProvider`
  - `JsonbBuilder`
  - `Jsonb`
- Route `Jsonb#fromJson` and `Jsonb#toJson` overloads through Micronaut Serialization using `Argument.of(Type)` where possible.
- Support `JsonbConfig` behavior:
  - formatting
  - encoding
  - null handling
  - strict I-JSON
  - property naming strategy
  - property order strategy
  - binary data strategy
  - date and number formats
  - locale
  - adapters
  - serializers
  - deserializers
  - creator-parameters-required
  - property visibility strategy
- Extend existing JSON-B annotation support where needed for runtime behavior, especially:
  - `JsonbTypeAdapter`
  - `JsonbTypeSerializer`
  - `JsonbTypeDeserializer`
  - `JsonbVisibility`
- Prefer generated Micronaut Serialization serializers/deserializers, Micronaut introspection, and `BeanContext` for all object construction and custom components.
- Add an internal reflection fallback only in `serde-jsonb`, used after generated serializer/deserializer, Micronaut introspection, and `BeanContext` resolution fail.
- The fallback must not be a general alternative to annotation processing. It should be used only when the target type has no annotations or metadata that the Micronaut processor can pick up, and there is no other viable way to implement required JSON-B API or TCK behavior.

### TCK And Evidence

- Add standalone TCK Gradle modules modeled after the JAX-RS TCK setup:
  - normal TCK run task
  - single-test task
  - known-failure task
  - known-failure refresh/discovery task
  - XML known-failure tracker
  - guard that fails when known failures unexpectedly pass
- Add root aggregate tasks:
  - `jakartaJsonpTck`
  - `jakartaJsonbTck`
  - `jakartaJsonTck`
- Add evidence collection script/workflow producing:
  - sanitized JUnit XML
  - summary Markdown
  - HTML index
  - artifact SHA-256
  - commit SHA
  - product/module versions
  - workflow URL
- Store/publish JSON-P and JSON-B evidence separately, but allow a combined summary for release review.

### Documentation

- Update docs to explain:
  - `micronaut-serde-jsonp`: existing Parsson-backed JSON-P stream integration.
  - `micronaut-serde-jsonp-impl`: Micronaut-native JSON-P provider with no Parsson.
  - `micronaut-serde-jsonb`: JSON-B runtime provider.
- Add dependency examples and provider-loading notes.
- Document that JSON-P implementation avoids reflection entirely.
- Document that JSON-B may use reflection only as a compatibility fallback for spec-required behavior.

## Test Plan

- Unit tests for `serde-jsonp-impl`:
  - provider discovery without Parsson
  - parser/generator events
  - reader/writer behavior
  - builders and immutable value model
  - duplicate-key strategies
  - pointer, patch, merge patch, diff, merge diff
  - numeric precision and string escaping
- Unit tests for `serde-jsonb`:
  - provider discovery without Yasson
  - every `Jsonb#fromJson` / `toJson` overload
  - config-driven naming, ordering, nulls, dates, numbers, binary data, locale
  - adapters, serializers, deserializers
  - BeanContext-based custom component creation
- reflection fallback only when generated serializers/introspection are unavailable and the type has no processor-visible metadata that could be used instead
- TCK commands:
  - `./gradlew :micronaut-tests:micronaut-jsonp-tck:jakartaJsonpTck`
  - `./gradlew :micronaut-tests:micronaut-jsonb-tck:jakartaJsonbTck`
  - `./gradlew jakartaJsonTck`
- Add CI workflow for both standalone TCKs and evidence generation.
- Add dependency verification checks proving:
  - `serde-jsonp-impl` does not resolve Parsson.
  - `serde-jsonb` does not resolve Yasson.

## Assumptions

- Target version is the current repo version, `3.1.0-SNAPSHOT`; any new public APIs should use `@since 3.1.0`.
- Use standalone JSON-P and JSON-B TCKs, not Jakarta Platform TCKs.
- Keep `serde-jsonp` available and documented; do not deprecate it in this change.
- JSON-P implementation must be reflection-free.
- JSON-B may use reflection only inside `serde-jsonb`, only after Micronaut Serialization generated serializer/introspection paths fail and only for types without processor-visible metadata where reflection is required to satisfy JSON-B API/TCK behavior.
- Initial TCK targets should match the repo's current Jakarta API baselines: JSON-P 2.1 and JSON-B 3.0.
