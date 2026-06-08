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

The following local result was captured on GraalVM Java 25 from a rerun of the
published benchmark classes, `UserBeanSerdeBenchmark` and
`PropertyAccessShapeBenchmark`, with 3 forks, 5 warmup iterations, and 5
measurement iterations with 1-second iterations and `-prof gc`.

| Benchmark | Stack | Score |
| --- | --- | ---: |
| `serialize` | Jackson Databind | 386747.328 ops/s |
| `serialize` | Jackson Databind Blackbird | 384944.900 ops/s |
| `serialize` | Serde Jackson generated | 461673.850 ops/s |
| `serialize` | Serde Jackson runtime | 408845.913 ops/s |
| `deserialize` | Jackson Databind | 3755.969 ns/op |
| `deserialize` | Jackson Databind Blackbird | 3667.105 ns/op |
| `deserialize` | Serde Jackson generated | 3112.711 ns/op |
| `deserialize` | Serde Jackson runtime | 3248.889 ns/op |
| `roundTrip` | Jackson Databind | 6696.438 ns/op |
| `roundTrip` | Jackson Databind Blackbird | 6630.402 ns/op |
| `roundTrip` | Serde Jackson generated | 5250.939 ns/op |
| `roundTrip` | Serde Jackson runtime | 5698.729 ns/op |

![UserBeanSerdeBenchmark local results](user-bean-benchmark-results.svg)

Generated Micronaut Serialization led serialization, deserialization, and round
trip in this run:

- Serialization throughput was about 19.4% higher than Jackson Databind and
  about 12.9% higher than runtime Serde.
- Runtime Serde serialization was about 5.7% higher than Jackson Databind.
- Generated Serde deserialization was about 20.7% faster than Jackson Databind and
  about 4.4% faster than runtime Serde.
- Generated Serde round trip was about 27.5% faster than Jackson Databind and
  about 8.5% faster than runtime Serde.

The serialization GC-profiler row measured generated Serde at about
`6008 B/op`, runtime Serde at about
`6072 B/op`, and Jackson Databind at about
`6128 B/op` after releasing the Jackson `BufferRecycler`
acquired by `JacksonJsonMapper.writeValueAsBytes`.

## Property Access Results

`PropertyAccessShapeBenchmark` isolates object property binding mechanics while
keeping the JSON shape constant. It compares a 10-scalar-property bean bound via
constructor arguments, JavaBean getters/setters, and public fields.

The checked-in chart uses the same GraalVM Java 25 rerun as the user-bean
results: 3 forks, 5 warmup iterations, and 5 measurement iterations with
1-second iterations and `-prof gc`.

### Serialization Throughput

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 3188619.209 ops/s | 3829079.071 ops/s | 4054851.835 ops/s | 3611367.522 ops/s |
| Getter/setter | 3230420.652 ops/s | 3800296.461 ops/s | 4047709.891 ops/s | 3649161.750 ops/s |
| Field | 3210080.485 ops/s | 3236783.577 ops/s | 4028662.688 ops/s | 3628848.310 ops/s |

### Deserialization Average Time

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 621.558 ns/op | 513.990 ns/op | 275.386 ns/op | 331.476 ns/op |
| Getter/setter | 353.467 ns/op | 324.126 ns/op | 276.121 ns/op | 286.697 ns/op |
| Field | 349.391 ns/op | 348.772 ns/op | 274.427 ns/op | 285.839 ns/op |

### Round Trip Average Time

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 937.242 ns/op | 779.151 ns/op | 562.716 ns/op | 643.359 ns/op |
| Getter/setter | 731.758 ns/op | 643.983 ns/op | 564.177 ns/op | 601.727 ns/op |
| Field | 717.164 ns/op | 729.498 ns/op | 565.911 ns/op | 624.377 ns/op |

![PropertyAccessShapeBenchmark local results](property-access-shape-benchmark-results.svg)

Generated Serde has the highest serialization throughput, fastest
deserialization average time, and fastest round-trip average time for all three
property-access shapes in this matrix. Runtime Serde serialization also beats
Jackson Databind for all three shapes after switching runtime property reads to
the non-allocating Core `BeanPropertyImpl` read path. Runtime serialization
allocation is now about `776 B/op`, while generated Serde remains lower at about
`712 B/op`. Runtime round-trip allocation for mutable shapes is about
`1728-1760 B/op`; generated Serde remains lower at about
`1632 B/op` in this run.

## Focused Profiling Findings

The concerning property-access cases were rerun with async-profiler after the
`KeysSupport` contribution layout was flattened to `Object[]` slots. The
benchmark setup also validates the concrete generated/runtime serializer and
deserializer classes so generated and runtime paths are not accidentally mixed.

Current getter/setter deserialization result from the full rerun:

| Stack | Score |
| --- | ---: |
| Jackson Databind | 353.467 ns/op |
| Jackson Databind Blackbird | 324.126 ns/op |
| Serde Jackson generated | 276.121 ns/op |
| Serde Jackson runtime | 286.697 ns/op |

Current constructor serialization result from the full rerun:

| Stack | Score |
| --- | ---: |
| Jackson Databind | 3188619.209 ops/s |
| Jackson Databind Blackbird | 3829079.071 ops/s |
| Serde Jackson generated | 4054851.835 ops/s |
| Serde Jackson runtime | 3611367.522 ops/s |

GC profiling for getter/setter deserialization:

| Stack | Allocation |
| --- | ---: |
| Jackson Databind | 1072 B/op |
| Jackson Databind Blackbird | 1032 B/op |
| Serde Jackson generated | 872 B/op |
| Serde Jackson runtime | 872 B/op |

The earlier one-fork getter/setter deserialization outlier did not reproduce in
this three-fork GraalVM run. Generated Serde is fastest for getter/setter
deserialization at `276.121 ns/op`, ahead of runtime Serde at
`286.697 ns/op` and Jackson Databind Blackbird at
`324.126 ns/op`. The generated/runtime spread still varies by shape and is driven by
property access, `DerProperty` dispatch, and boxed primitive movement rather
than decode-key dispatch.

A measured backend-neutral sourcegen alternative replaced nullable scalar
primitive decoders with `decodeNull()` plus primitive decoders. It preserved
behavior, but regressed the focused getter/setter generated result in local
experiments, so it is not part of the retained changes.

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
