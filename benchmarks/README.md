# Serialization Benchmarks Runbook

This module contains JMH microbenchmarks for Micronaut Serialization
performance. The configured benchmark task currently includes:

- `io.micronaut.serde.StartupBenchmark`
- `io.micronaut.serde.SourceGenRoutingBenchmark`
- `io.micronaut.serde.UserBeanSerdeBenchmark`
- `io.micronaut.serde.PropertyAccessShapeBenchmark`
- `io.micronaut.serde.PropertyAccessOrderBenchmark`
- `io.micronaut.serde.PropertyValueKindBenchmark`
- `io.micronaut.serde.PropertyValueKindSerializationCeilingBenchmark`
- `io.micronaut.serde.PropertyValueKindSerializationLifecycleBenchmark`
- `io.micronaut.serde.BeanIntrospectionAccessBenchmark`
- `io.micronaut.serde.SpecificObjectDeserializerComplexShapeBenchmark`

Additional benchmark sources may exist for focused local investigation, but
the checked-in results below are limited to Micronaut Serialization and Jackson
Databind/Blackbird comparisons.

## Run

From repository root:

```bash
./gradlew :micronaut-benchmarks:jmh
```

To build the standalone JMH jar and run a focused local benchmark:

```bash
./gradlew -q :micronaut-benchmarks:jmhJar
repo=$PWD
tmpdir=$(mktemp -d /tmp/mn-jmh.XXXXXX)
cd "$tmpdir"
jar xf "$repo/benchmarks/build/libs/micronaut-benchmarks-3.0.1-SNAPSHOT-jmh.jar"
java -cp "$tmpdir:$tmpdir/*" org.openjdk.jmh.Main '.*UserBeanSerdeBenchmark.*' -wi 5 -i 10 -f 3 -w 1s -r 1s -tu ns
```

The defaults in `benchmarks/build.gradle` are intentionally lightweight for
developer feedback:

- `fork = 10`
- `iterations = 1`
- `warmupIterations = 0`

Use longer warmup and measurement iterations for performance conclusions.

## User Bean Results

`UserBeanSerdeBenchmark` compares a `Users` payload modeled after the
`fabienrenaud/java-json-benchmark` users data shape. It includes nested user
objects, friend objects, arrays/lists, strings, numbers, and booleans.

The following local result was captured on JDK 25 from a rerun of the
published benchmark classes, `UserBeanSerdeBenchmark` and
`PropertyAccessShapeBenchmark`, with 1 fork, 5 warmup iterations, and 5
measurement iterations with 1-second iterations and `-prof gc`.

| Benchmark | Stack | Score |
| --- | --- | ---: |
| `serialize` | Jackson Databind | 296434.520 ops/s |
| `serialize` | Jackson Databind Blackbird | 296601.980 ops/s |
| `serialize` | Serde Jackson generated | 397529.012 ops/s |
| `serialize` | Serde Jackson runtime | 328679.764 ops/s |
| `deserialize` | Jackson Databind | 4343.504 ns/op |
| `deserialize` | Jackson Databind Blackbird | 4169.716 ns/op |
| `deserialize` | Serde Jackson generated | 3871.981 ns/op |
| `deserialize` | Serde Jackson runtime | 4087.727 ns/op |
| `roundTrip` | Jackson Databind | 8501.947 ns/op |
| `roundTrip` | Jackson Databind Blackbird | 8137.167 ns/op |
| `roundTrip` | Serde Jackson generated | 6274.849 ns/op |
| `roundTrip` | Serde Jackson runtime | 6953.842 ns/op |

![UserBeanSerdeBenchmark local results](user-bean-benchmark-results.svg)

Generated Micronaut Serialization led serialization, deserialization, and round
trip in this run:

- Serialization throughput was about 34.1% higher than Jackson Databind and
  about 20.9% higher than runtime Serde.
- Runtime Serde serialization was about 10.9% higher than Jackson Databind.
- Runtime Serde deserialization was faster than both Jackson modes, but still
  about 5.6% slower than generated Serde.
- Generated Serde round trip was about 35.5% faster than Jackson Databind and
  about 10.8% faster than runtime Serde.

The serialization GC-profiler row measured generated Serde at about `6056 B/op`,
runtime Serde at about `6121 B/op`, and Jackson Databind at about `6176 B/op`
after releasing the Jackson `BufferRecycler` acquired by
`JacksonJsonMapper.writeValueAsBytes`.

## Property Access Results

`PropertyAccessShapeBenchmark` isolates object property binding mechanics while
keeping the JSON shape constant. It compares a 10-scalar-property bean bound via
constructor arguments, JavaBean getters/setters, and public fields.

The checked-in chart uses the same JDK 25 rerun as the user-bean results: 1
fork, 5 warmup iterations, and 5 measurement iterations with 1-second
iterations and `-prof gc`.

### Serialization Throughput

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 2489730.322 ops/s | 2621700.877 ops/s | 3129365.168 ops/s | 2721379.252 ops/s |
| Getter/setter | 2473221.289 ops/s | 2191072.484 ops/s | 3153901.624 ops/s | 2477595.284 ops/s |
| Field | 2354108.273 ops/s | 2436811.256 ops/s | 3179993.557 ops/s | 2714157.274 ops/s |

### Deserialization Average Time

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 724.640 ns/op | 592.184 ns/op | 463.715 ns/op | 489.959 ns/op |
| Getter/setter | 467.885 ns/op | 440.687 ns/op | 494.398 ns/op | 435.826 ns/op |
| Field | 471.130 ns/op | 466.211 ns/op | 429.459 ns/op | 431.469 ns/op |

### Round Trip Average Time

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 1151.411 ns/op | 998.567 ns/op | 818.462 ns/op | 953.365 ns/op |
| Getter/setter | 920.895 ns/op | 941.657 ns/op | 768.391 ns/op | 902.130 ns/op |
| Field | 907.985 ns/op | 922.468 ns/op | 770.478 ns/op | 896.809 ns/op |

![PropertyAccessShapeBenchmark local results](property-access-shape-benchmark-results.svg)

Generated Serde has the highest serialization throughput for all three shapes
in this matrix. For deserialization, generated Serde is fastest for constructor
and field binding, while runtime Serde is fastest for getter/setter binding in
this local run. Runtime Serde serialization beats both Jackson modes for all
three shapes after switching runtime property reads to the non-allocating Core
`BeanPropertyImpl` read path. Runtime serialization allocation is now about
`848 B/op`, while generated Serde remains lower at about `760 B/op`. Runtime
round-trip allocation for mutable shapes is about `1856 B/op`; generated Serde
remains the fastest round-trip stack for all three shapes in this run.

## Focused Profiling Findings

The concerning property-access cases were rerun with async-profiler after the
`KeysSupport` contribution layout was flattened to `Object[]` slots. The
benchmark setup also validates the concrete generated/runtime serializer and
deserializer classes so generated and runtime paths are not accidentally mixed.

Current getter/setter deserialization result from the full rerun:

| Stack | Score |
| --- | ---: |
| Jackson Databind | 467.885 ns/op |
| Jackson Databind Blackbird | 440.687 ns/op |
| Serde Jackson generated | 494.398 ns/op |
| Serde Jackson runtime | 435.826 ns/op |

Current constructor serialization result from the full rerun:

| Stack | Score |
| --- | ---: |
| Jackson Databind | 2489730.322 ops/s |
| Jackson Databind Blackbird | 2621700.877 ops/s |
| Serde Jackson generated | 3129365.168 ops/s |
| Serde Jackson runtime | 2721379.252 ops/s |

GC profiling for getter/setter deserialization:

| Stack | Allocation |
| --- | ---: |
| Jackson Databind | 1072 B/op |
| Jackson Databind Blackbird | 1032 B/op |
| Serde Jackson generated | 920 B/op |
| Serde Jackson runtime | 1008 B/op |

The current getter/setter deserialization result is mixed: runtime Serde is
ahead of both Jackson modes in this one-fork run, while generated Serde is
behind the Jackson baselines for that one shape. Generated Serde remains ahead
for constructor and field deserialization. The generated/runtime spread varies
by shape and is still driven by property access, `DerProperty` dispatch, and
boxed primitive movement rather than decode-key dispatch.

A measured backend-neutral sourcegen alternative replaced nullable scalar
primitive decoders with `decodeNull()` plus primitive decoders. It preserved
behavior, but regressed the focused getter/setter generated result to about
`340.847 ns/op` while Blackbird measured about `327.237 ns/op`, so it is not
part of the retained changes.

The remaining runtime Serde deserialization gap is a different issue. The simple
runtime path no longer uses `PropertiesBag.Consumer`, but it still pays
`DerProperty` and runtime introspection getter/setter costs that are
intentionally absent from generated Serde.

## Reproducibility Notes

- Treat single short JMH runs as directional only.
- Keep stack comparisons in the same command where practical.
- Confirm the concrete serializer/deserializer implementations in benchmark
  setup so generated and runtime paths are not accidentally mixed.
- Use async-profiler and `-prof gc` when a small runtime delta needs an
  allocation-vs-CPU explanation.
