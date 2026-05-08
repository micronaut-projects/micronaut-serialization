# Serde Processor Development Guide

This module generates compile-time serializers and deserializers. Generated serde code is runtime hot-path code and should be treated like hand-written performance-sensitive code.

## Generated Serde Performance

- Generate serdes under the assumption that they run on the serialization/deserialization performance fast path; shape code for that hot path first.
- Keep generated positive-flow serialization and deserialization as direct as possible.
- Do not add repeated context lookups, feature checks, annotation checks, or serializer/deserializer discovery inside generated per-property hot paths.
- Any value derived from `EncoderContext`, `DecoderContext`, `Argument`, configuration, or annotation metadata that is needed during the positive flow should be computed once during specific serde creation or constructor setup and stored in a generated field.
- Prefer cached final fields for generated property serializers, deserializers, feature flags, and precomputed property arguments.
- Avoid nullable serializer/deserializer fields in generated classes. Resolve required property serdes during specific creation or construction.
- Treat null handling in generated code as contract-driven, not defensive. Generated `Serializer.serialize` implementations should assume callers already excluded nulls; emit null branches only for generated API methods whose declared contract accepts null values, such as `ObjectSerializer.serializeInto`.
- Keep primitive decoding unboxed in the normal path. Use nullable primitive decoder methods only for behavior that specifically requires distinguishing explicit null, and cache the controlling setting.
- Avoid temporary locals in generated code when a direct setter or field assignment is equally clear and does not duplicate side effects.

## Sourcegen Practices

- Use the repository `micronaut-sourcegen` skill before adding or changing Sourcegen-based cserdes.
- Model generated code with Micronaut Sourcegen APIs instead of string assembly.
- Reference generated constants and generated fields through the corresponding Sourcegen definitions where practical.
- Keep generated source readable enough for compile-time tests to assert shape, but prefer runtime behavior and hot-path cost over cosmetic source layout.
- When adding or changing sourcegen cserdes, include behavior tests that compare the generated path against the runtime serde path and Jackson Databind for the same models and payloads.
- Compile-time tests should validate expected generated structure and behavior, not broad negative cases unrelated to the requested generated contract.
