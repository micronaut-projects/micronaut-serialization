# Proposal: Lower-Overhead Bean Property Access For Serde Runtime

## Problem

Micronaut Serde runtime can precompute property keys and direct scalar kinds, but primitive bean properties still cross Micronaut Core introspection as `Object`.

For serialization, primitive getters and fields box through `UnsafeBeanReadProperty.getUnsafe`, then Serde immediately unboxes the wrapper for `Encoder.encodeInt`, `encodeLong`, `encodeBoolean`, `encodeDouble`, and the other scalar methods.

For deserialization, runtime direct scalar decode can read primitives from the decoder, but `DeserBean.DerProperty` boxes the decoded value before calling `UnsafeBeanWriteProperty.setUnsafe`; the generated introspection then unboxes to call the setter or write the field.

Boxed and object properties do not have the same artificial cost. They are already references, and nullable handling is semantically meaningful.

The first `BeanIntrospectionAccessBenchmark` pass shows this is not only a primitive problem. Reading ten object properties directly took about `4.7-4.9 ns/op`, while `UnsafeBeanReadProperty.getUnsafe` took about `16.4 ns/op`. Writing ten object properties directly took about `8.5 ns/op` for setter/field shapes, while `UnsafeBeanWriteProperty.setUnsafe` took about `19.7 ns/op`. Primitive-specific APIs would address boxing, but Core also needs a lower-overhead generated dispatch shape for reference properties if Serde runtime is expected to consistently beat Jackson Databind Blackbird.

A focused `-prof gc` follow-up on May 20, 2026 confirmed the allocation split and corrected the reference-property assumption. Unsafe object reads allocate `160 B/op` for ten properties in the current generated introspection path, so there is a base no-argument read dispatch allocation before primitive boxing is considered. Primitive unsafe reads allocate `328 B/op`, which is roughly that base read allocation plus another `168 B/op` of primitive wrapper cost. Constructor primitive writes allocate `296 B/op`; setter and field primitive writes allocate `168 B/op` in the latest write-focused run. This means the first Serde runtime serialization target should be removing no-argument read dispatch allocation, followed by primitive-specific access to remove the extra wrapper cost.

## Current Hot Path

Serialization:

1. `SerBean` creates `PropSerProperty` from `BeanIntrospection.getBeanReadProperties()`.
2. `PropSerProperty.get` delegates to `UnsafeBeanReadProperty.getUnsafe(bean)`.
3. `SimpleObjectSerializer` receives an `Object` value.
4. Direct scalar serialization unboxes that `Object` for the encoder scalar method.

Deserialization:

1. `DeserBean.DerProperty` can select direct scalar decode from `DecoderValueKind`.
2. Primitive scalar decode still returns or stores wrapper values in the generic path.
3. `UnsafeBeanWriteProperty.setUnsafe(bean, value)` accepts the wrapper.
4. Generated introspection unboxes before invoking the setter or writing the field.

Generated Serde avoids this for simple generated source paths by reading fields/getters and assigning setters directly with primitive values.

## Core API Direction

Do not add methods to existing public unsafe interfaces. Add optional internal Core extension interfaces that generated introspections can implement when a property is primitive, and consider a compact indexed dispatch interface that avoids one wrapper object per property access for reference properties.

Possible shape:

```java
@Internal
public interface PrimitiveBeanReadProperty<B> {
    PrimitiveKind primitiveKind();

    default int getIntUnsafe(B bean) {
        throw new UnsupportedOperationException();
    }

    default long getLongUnsafe(B bean) {
        throw new UnsupportedOperationException();
    }

    default boolean getBooleanUnsafe(B bean) {
        throw new UnsupportedOperationException();
    }

    default double getDoubleUnsafe(B bean) {
        throw new UnsupportedOperationException();
    }
}
```

Use either one kind-switched interface or type-specific subinterfaces such as `IntBeanReadProperty<B>` only after benchmarking dispatch cost. The same applies to write-side interfaces.

`PrimitiveKind` should be a Core-local model, independent from Serde's `DecoderValueKind`.

For boxed and object properties, a separate internal shape could expose an indexed read/write plan from the introspection:

```java
@Internal
public interface IndexedBeanPropertyAccess<B> {
    Object getUnsafe(B bean, int propertyIndex);

    void setUnsafe(B bean, int propertyIndex, Object value);
}
```

This is only useful if generated introspections can dispatch more cheaply than the current per-property `UnsafeBeanReadProperty`/`UnsafeBeanWriteProperty` objects. It must be benchmarked against direct access and against the existing property object path before any Core API is proposed.

## Core Implementation Direction

Keep existing generated introspection constructors and existing property implementations binary-compatible.

Before adding typed access, measure one low-risk cleanup in Core: if the generated dispatch path permits it, `BeanReadPropertyImpl.getUnsafe` should use `null` or a shared empty argument constant for no-argument reads instead of allocating or passing a fresh empty argument array. The latest read-focused benchmark makes this a first-order allocation target, not just noise: object unsafe reads allocate `160 B/op` for ten properties even though the values are already references.

Local Core source inspection confirms the shape: `AbstractInitializableBeanIntrospection.BeanPropertyImpl.getUnsafe` dispatches with `null`, while the read-only `BeanReadPropertyImpl.getUnsafe` dispatches with `new Object[0]`. Serde runtime serialization builds properties from `BeanIntrospection.getBeanReadProperties()`, so `SerBean.PropSerProperty.get(...)` currently reaches the `new Object[0]` path for ordinary runtime reads.

A Serde-local prototype confirms the diagnosis without changing Core APIs: keep the original `BeanReadProperty` for metadata, but use the matching full `BeanProperty` from `getBeanProperties()` for the hot `getUnsafe` call when available. On `PropertyAccessShapeBenchmark.serialize`, runtime allocation dropped from about `1008 B/op` to about `848 B/op`, and mutable-shape throughput improved from `3297506 -> 3536226 ops/s` for getter/setter and `3322797 -> 3634171 ops/s` for field. This is useful as a local workaround and as evidence that Core should remove the no-argument read allocation directly.

In `AbstractInitializableBeanIntrospection`, generated property implementations can expose primitive read/write only when the property argument type is primitive.

Generate primitive dispatch only for kinds present in the introspection:

1. `dispatchGetInt(int index, Object target)`
2. `dispatchGetLong(int index, Object target)`
3. `dispatchGetBoolean(int index, Object target)`
4. `dispatchGetDouble(int index, Object target)`
5. Matching primitive setter dispatch methods.

Only generate `byte`, `short`, `char`, and `float` variants if a benchmark shows that widening through `int`/`double` is not sufficient or introduces semantic cost.

Check the existing no-arg read-only path for avoidable empty-array allocation. If `BeanReadPropertyImpl.getUnsafe` passes a new empty array to dispatch, use `null` or a shared empty constant if the dispatch path permits it.

For reference properties, evaluate whether the generated introspection can expose the existing internal dispatch method behind an `@Internal` indexed interface so Serde can store integer property indexes instead of ten separate property objects. This should reduce wrapper dispatch overhead for object-heavy runtime serialization/deserialization without adding primitive-specific API surface to every reference property. Defer this broader object/reference API until mapper-level boxed/object benchmarks show a real user-facing gap after primitive serialization is fixed; current object serialization data already looks competitive.

## Serde Runtime Use

At Serde property-plan creation time, detect primitive-capable Core properties, indexed Core property access, and direct scalar-compatible Serde properties.

For serialization, use primitive property readers only when Serde can write a direct scalar without invoking a custom serializer or feature/format override.

For deserialization, use primitive property writers only when direct scalar decoding is selected and null handling has already been resolved.

For object and boxed properties, use indexed Core property access only when the property is a simple bean property and the existing property metadata still supplies annotations, argument data, views, default handling, and custom serializer/deserializer decisions. The indexed access path should replace only the hot get/set call, not the property model.

Constructor-bound beans still require `Object[]` today. Avoid changing that in the first pass; measure setter and field wins first. Constructor primitive buffers or typed constructor binders are a second, higher-risk proposal.

## GraalVM And Reflection-Free Constraints

Do not use reflection, `MethodHandle`, `LambdaMetafactory`, or runtime bytecode generation for the production path.

The implementation should remain compile-time generated introspection bytecode. Runtime prototypes can use method handles only as a throwaway control to estimate the upper bound.

Reflection-required fields or methods should fall back to the existing object path and should not define the optimized contract.

## Risks

1. Adding methods to existing public interfaces would be binary risky; use new optional internal interfaces instead.
2. Primitive and indexed dispatch can increase generated bytecode size and native-image footprint if every introspection gets every method.
3. Per-property switches inside Serde's hot loops can erase the gain; choose the accessor plan once during serializer/deserializer construction.
4. Explicit `null` for primitives, Kotlin defaults, and `fail-on-null-for-primitives` must retain current behavior.
5. Boxed and object-only models may not benefit; their benchmarks must remain neutral.

## Benchmark Plan

Use `PropertyAccessShapeBenchmark` as the mapper-level benchmark. It already compares constructor, getter/setter, and field shapes across Jackson Databind, Jackson Databind Blackbird, generated Serde, and runtime Serde.

Add a focused Core/Serde accessor benchmark that compares:

1. Direct Java field/getter/setter access.
2. Current `UnsafeBeanReadProperty` and `UnsafeBeanWriteProperty`.
3. Proposed primitive read/write interfaces.
4. Proposed indexed reference read/write interface.
5. Boxed equivalents: `Integer`, `Long`, `Boolean`, and `Double`.
6. Object equivalents: `String`.

Run with `-prof gc` and values outside common wrapper caches, especially `int > 127`, large `long`, and `double`.

The first MethodHandle control should not be used as the production model. An array of exact `MethodHandle` readers/writers was slower than current unsafe introspection for most getter/setter and field reads/writes. It only helped constructor instantiation. That result points away from MethodHandle arrays and toward Core-generated typed property implementations or generated indexed dispatch methods.

The focused GC run is stored at `benchmarks/build/results/jmh/bean-introspection-access-focused-gc-2026-05-20.json`. Later five-warmup/five-measurement controls are stored at:

1. `benchmarks/build/results/jmh/bean-introspection-access-write-core-gap-2026-05-20.json`
2. `benchmarks/build/results/jmh/bean-introspection-access-read-core-gap-2026-05-20.json`

The write control measured ten-property write operations as:

| Shape | Value kind | Direct ns/op | Direct B/op | MethodHandle ns/op | MethodHandle B/op | Unsafe introspection ns/op | Unsafe introspection B/op |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Constructor | Primitive | 4.9 | 72 | 9.0 | 72 | 44.6 | 296 |
| Constructor | Object | 4.6 | 56 | 8.8 | 56 | 19.2 | 56 |
| Getter/setter | Primitive | 4.2 | ~0 | 24.7 | ~0 | 31.0 | 168 |
| Getter/setter | Object | 9.3 | ~0 | 27.3 | ~0 | 30.1 | ~0 |
| Field | Primitive | 4.2 | ~0 | 23.8 | ~0 | 31.2 | 168 |
| Field | Object | 6.5 | ~0 | 30.3 | ~0 | 30.4 | ~0 |

This strengthens the write-side direction: primitive write access has both CPU and allocation cost, while object/reference write dispatch is primarily a CPU target. MethodHandle arrays remain a useful negative control, not the production design.

The read control measured ten-property read operations as:

| Shape | Value kind | Direct ns/op | Direct B/op | MethodHandle ns/op | MethodHandle B/op | Unsafe introspection ns/op | Unsafe introspection B/op |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Constructor | Primitive | 2.2 | ~0 | 25.9 | ~0 | 41.4 | 328 |
| Constructor | Object | 5.1 | ~0 | 26.5 | ~0 | 30.9 | 160 |
| Getter/setter | Primitive | 2.2 | ~0 | 25.5 | ~0 | 39.8 | 328 |
| Getter/setter | Object | 5.1 | ~0 | 26.5 | ~0 | 31.3 | 160 |
| Field | Primitive | 2.2 | ~0 | 23.5 | ~0 | 39.6 | 328 |
| Field | Object | 6.5 | ~0 | 25.6 | ~0 | 31.0 | 160 |

This changes the priority order: remove the base no-argument read allocation first, then add primitive typed access to remove the extra primitive wrapper allocation and CPU cost. MethodHandle arrays again avoid allocation but remain much slower than direct reads, so they are still only a negative control.

Success criteria:

1. Runtime Serde primitive-heavy serialization improves by at least 5% for getter/setter and field shapes.
2. Runtime Serde primitive-heavy allocation drops materially in `gc.alloc.rate.norm`.
3. Boxed/object-only benchmarks improve if indexed access is implemented, or remain neutral if only primitive access is implemented.
4. Generated Serde remains neutral, since it should already be direct.
