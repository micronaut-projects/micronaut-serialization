# JSON-B Reflection Fallback via Runtime Serde Introspections

## Summary

Refactor `MicronautJsonbReflectionProvider` so ordinary reflection fallback object handling flows through Micronaut Serialization's existing `ObjectSerializer` and `ObjectDeserializer`, reusing the internal `SerBean` and `DeserBean` machinery without making those types public. The JSON-B reflection provider should focus on producing reflection-backed `BeanIntrospection` instances with the right Serde metadata shape.

## Public API / SPI

- Add a small resolver SPI to `SerdeIntrospections`:
  - `RuntimeIntrospectionKind { SERIALIZATION, DESERIALIZATION }`
  - `RuntimeIntrospectionRequest<T>` carrying `Argument<T>` and kind.
  - `RuntimeIntrospectionResolver` with `cacheKey()` and `resolve(request)`.
  - `SerdeIntrospections withRuntimeIntrospectionResolver(RuntimeIntrospectionResolver resolver)`.
- Add a package-private `serde-api` wrapper implementation that:
  - asks the resolver first;
  - caches resolver-built `BeanIntrospection` instances by kind, `Argument`, and resolver cache key;
  - falls back to the delegate `SerdeIntrospections` when the resolver returns empty.
- Add plumbing overloads so a cloned mapper can use resolver-backed introspections:
  - `ObjectMapper.cloneWithConfiguration(..., SerdeIntrospections introspections)`.
  - `SerdeRegistry.cloneWithConfiguration(..., SerdeIntrospections introspections)`.
  - Implement in Jackson, JSON-P, and `DefaultSerdeRegistry`.
- Keep `SerBean`, `DeserBean`, and their registries package-private/internal.

## Implementation Changes

- In `serde-jsonb`, add an internal `JsonbRuntimeIntrospectionResolver` used only by `MicronautJsonbReflectionProvider`.
- The resolver builds cached reflection-backed `BeanIntrospection` implementations for non-generated JSON-B fallback types:
  - reflected read/write properties are exposed as `BeanReadProperty` and `BeanWriteProperty`;
  - reflected constructors or `@JsonbCreator` factories are exposed through `BeanConstructor`;
  - annotation metadata is synthesized using existing Serde metadata shapes already used by JSON-B annotation transformers, including property names, ignores, order, formats, adapters, serializers, deserializers, and subtype metadata where directly representable.
- The runtime `BeanIntrospection` implementation is intentionally a subset: implement only the methods and metadata shape needed for `SerBean`/`DeserBean` to satisfy the JSON-B TCK and focused regression tests.
- Generated Micronaut introspections remain the preferred/default path. The runtime resolver should return empty for generated-introspected types; metadata issues in generated introspections should be fixed in that path rather than masked by reflection.
- JSON-B type-info metadata should be represented through the same Serde metadata shape for generated and runtime introspections. Runtime introspections synthesize `SerSubtyped`, `TYPE_NAME`, `TYPE_NAMES`, and `TYPE_PROPERTY` metadata instead of buffering serialized JSON and rewriting discriminator properties afterward.
- Reflection-backed constructors, factories, fields, and accessors should call `setAccessible(true)` where needed so fallback types do not need explicit public constructors or public members.
- Update the reflection provider's borrowed/standalone mapper creation to clone with JSON-B serde configuration plus `serdeIntrospections.withRuntimeIntrospectionResolver(jsonbResolver)`.
- Replace ordinary object `ReflectionFallback.toJsonValue` and `ReflectionFallback.fromJsonValue` paths with `runtimeMapper.writeValue(...)` and `runtimeMapper.readValue(...)`.
- Keep specialized glue outside the runtime introspection migration for this first change:
  - scalar, JSON-P, map/collection/array normalization;
  - configured JSON-B adapters/serializers/deserializers;
  - existing `JsonbTypeInfoSupport` validation where Serde subtype metadata is not enough yet;
  - generated-serde fast path and streaming guards.
- Leave existing validation behavior intact initially, then delete duplicated reflection traversal helpers only after equivalent tests pass through `SerBean`/`DeserBean`.

## Test Plan

- Add focused tests proving non-introspected JSON-B beans now serialize/deserialize through the runtime introspection path.
- Cover metadata cases:
  - `@JsonbProperty`
  - `@JsonbTransient`
  - `@JsonbCreator`
  - Java records
  - `@JsonbPropertyOrder`
  - `@JsonbDateFormat`
  - `@JsonbNumberFormat`
  - property visibility strategy
  - type adapter
  - serializer
  - deserializer
  - generated and runtime `@JsonbTypeInfo`
- Add a cache test: repeated serialization/deserialization of the same type/config builds one runtime introspection per direction.
- Keep current generated-provider tests, streaming/read guards, reflection source guards, injected/current-context tests, and TCK checks.
- Verify with:
  - `./gradlew :micronaut-serde-jsonb:test`
  - `./gradlew :micronaut-tests:micronaut-jsonb-tck:jakartaJsonbTck`
  - `./gradlew jakartaJsonTck`
  - `./gradlew :micronaut-serde-jsonb:assertNoYassonDependency`

## Assumptions

- First migration is object-model-first: `SerBean`/`DeserBean` handle ordinary object fallback, while specialized JSON-B glue remains in the provider.
- Resolver SPI is a compatible public API addition with `@since 3.1.0`, matching current `projectVersion=3.1.0-SNAPSHOT`.
- Runtime introspections are internal implementation objects; the only public surface is the resolver SPI, not the reflection implementation or `SerBean`/`DeserBean`.
