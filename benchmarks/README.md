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

### Format comparison: JSON vs BSON vs CBOR (`FormatComparisonBenchmark`)

Same `Users` payload (java-json-benchmark style) across Micronaut Serde format
runtimes only (no Jackson Databind). Not part of the default JMH includes list.

```bash
./gradlew :micronaut-benchmarks:jmh \
  -Pjmh.includes='.*FormatComparisonBenchmark.*' \
  -Pjmh.forks=3 \
  -Pjmh.warmupIterations=5 \
  -Pjmh.iterations=10 \
  -Pjmh.warmup=1s \
  -Pjmh.timeOnIteration=1s
```

#### Latest Local Results (`FormatComparisonBenchmark`)

Environment and JMH settings:

- JDK: Temurin OpenJDK 25.0.4
- Forks: 3
- Warmup iterations: 5 × 1s
- Measurement iterations: 10 × 1s
- Mode: throughput (`ops/s`, higher is better)
- Total run time: ~6m 56s
- Payload: `Users` (one user, nested friends/tags)

**Encoded size** (bytes after `writeValueAsBytes`, same object):

| Stack | Encoded size |
| --- | ---: |
| Serde JSON | 2153 bytes |
| Serde BSON Binary | 2460 bytes |
| Serde Jackson CBOR | **1852 bytes** |

**Throughput:**

| Operation | Serde JSON | Serde BSON Binary | Serde Jackson CBOR |
| --- | ---: | ---: | ---: |
| `serialize` | 276307.257 ops/s | 175418.104 ops/s | **516586.278 ops/s** |
| `deserialize` | **324051.390 ops/s** | 172018.477 ops/s | 299655.718 ops/s |
| `roundTrip` | 145548.614 ops/s | 84372.112 ops/s | **185160.443 ops/s** |

Relative to Serde JSON (1.00×):

| Operation | JSON | BSON Binary | CBOR |
| --- | ---: | ---: | ---: |
| `serialize` | 1.00× | 0.63× | **1.87×** |
| `deserialize` | 1.00× | 0.53× | 0.92× |
| `roundTrip` | 1.00× | 0.58× | **1.27×** |
| Encoded size | 1.00× | 1.14× | **0.86×** |

99.9% confidence intervals:

| Benchmark | Stack | Score ± Error |
| --- | --- | ---: |
| `serialize` | Serde JSON | 276307.257 ± 1635.828 ops/s |
| `serialize` | Serde BSON Binary | 175418.104 ± 1177.031 ops/s |
| `serialize` | Serde Jackson CBOR | 516586.278 ± 1449.930 ops/s |
| `deserialize` | Serde JSON | 324051.390 ± 849.133 ops/s |
| `deserialize` | Serde BSON Binary | 172018.477 ± 1999.295 ops/s |
| `deserialize` | Serde Jackson CBOR | 299655.718 ± 1179.970 ops/s |
| `roundTrip` | Serde JSON | 145548.614 ± 2460.030 ops/s |
| `roundTrip` | Serde BSON Binary | 84372.112 ± 405.257 ops/s |
| `roundTrip` | Serde Jackson CBOR | 185160.443 ± 729.726 ops/s |

### CBOR micro (`CborBenchmark`)

Single-format smoke numbers for small beans, not the multi-format comparison above. Not part of
the default JMH includes list.

```bash
./gradlew :micronaut-benchmarks:jmh -Pjmh.includes='.*CborBenchmark.*' -Pjmh.forks=1
```

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

The following local result was extracted from a full local
`:micronaut-benchmarks:jmh` run on GraalVM Java 25 with 3 forks, 5 warmup
iterations, and 5 measurement iterations with 1-second iterations and
`-prof gc`.

| Benchmark | Stack | Score |
| --- | --- | ---: |
| `serialize` | Jackson Databind | 394477.602 ops/s |
| `serialize` | Jackson Databind Blackbird | 391555.741 ops/s |
| `serialize` | Serde Jackson generated | 527496.722 ops/s |
| `serialize` | Serde Jackson runtime | 409800.044 ops/s |
| `deserialize` | Jackson Databind | 3282.474 ns/op |
| `deserialize` | Jackson Databind Blackbird | 3165.105 ns/op |
| `deserialize` | Serde Jackson generated | 3025.886 ns/op |
| `deserialize` | Serde Jackson runtime | 3119.831 ns/op |
| `roundTrip` | Jackson Databind | 6399.353 ns/op |
| `roundTrip` | Jackson Databind Blackbird | 6117.326 ns/op |
| `roundTrip` | Serde Jackson generated | 4865.063 ns/op |
| `roundTrip` | Serde Jackson runtime | 5164.739 ns/op |

![UserBeanSerdeBenchmark local results](user-bean-benchmark-results.svg)

Generated Micronaut Serialization led serialization, deserialization, and round
trip in this run:

- Serialization throughput was about 33.7% higher than Jackson Databind and
  about 28.7% higher than runtime Serde.
- Runtime Serde serialization was about 3.9% higher than Jackson Databind.
- Generated Serde deserialization was about 7.8% faster than Jackson Databind and
  about 3.0% faster than runtime Serde.
- Generated Serde round trip was about 24.0% faster than Jackson Databind and
  about 5.8% faster than runtime Serde.

The serialization GC-profiler row measured generated Serde at about
`6056 B/op`, runtime Serde at about
`6078 B/op`, and Jackson Databind at about
`6176 B/op` after releasing the Jackson `BufferRecycler`
acquired by `JacksonJsonMapper.writeValueAsBytes`.

## Property Access Results

`PropertyAccessShapeBenchmark` isolates object property binding mechanics while
keeping the JSON shape constant. It compares a 10-scalar-property bean bound via
constructor arguments, JavaBean getters/setters, and public fields.

The checked-in chart uses the same full local GraalVM Java 25 benchmark run as
the user-bean results: 3 forks, 5 warmup iterations, and 5 measurement
iterations with 1-second iterations and `-prof gc`.

### Serialization Throughput

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 3263383.436 ops/s | 3457901.852 ops/s | 4126252.251 ops/s | 3621693.071 ops/s |
| Getter/setter | 3177339.525 ops/s | 3017712.805 ops/s | 4174141.756 ops/s | 3601468.090 ops/s |
| Field | 3108074.535 ops/s | 3233629.410 ops/s | 4143213.274 ops/s | 3654192.086 ops/s |

### Deserialization Average Time

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 573.969 ns/op | 447.063 ns/op | 349.165 ns/op | 397.452 ns/op |
| Getter/setter | 354.234 ns/op | 334.726 ns/op | 345.968 ns/op | 314.796 ns/op |
| Field | 354.826 ns/op | 356.240 ns/op | 328.122 ns/op | 312.069 ns/op |

### Round Trip Average Time

| Shape | Jackson Databind | Jackson Databind Blackbird | Serde generated | Serde runtime |
| --- | ---: | ---: | ---: | ---: |
| Constructor | 890.600 ns/op | 768.249 ns/op | 628.316 ns/op | 710.635 ns/op |
| Getter/setter | 692.905 ns/op | 675.364 ns/op | 593.390 ns/op | 606.914 ns/op |
| Field | 695.133 ns/op | 691.246 ns/op | 597.422 ns/op | 606.917 ns/op |

![PropertyAccessShapeBenchmark local results](property-access-shape-benchmark-results.svg)

Generated Serde has the highest serialization throughput for all three
property-access shapes in this matrix. Runtime Serde serialization also beats
Jackson Databind for all three shapes after switching runtime property reads to
the Core primitive property access path.

Deserialization is now split by shape: generated Serde is fastest for
constructor binding, while runtime Serde is fastest for getter/setter and field
binding in this run. For mutable shapes, generated and runtime Serde both
allocate about `920 B/op` on deserialization and about
`1680 B/op` on round trip. Constructor runtime
deserialization still allocates about `144 B/op` more than generated
Serde because it uses the runtime introspection construction path.

## Focused Profiling Findings

The concerning property-access cases were rerun with async-profiler after the
`KeysSupport` contribution layout was flattened to `Object[]` slots. The
benchmark setup also validates the concrete generated/runtime serializer and
deserializer classes so generated and runtime paths are not accidentally mixed.

Current getter/setter deserialization result from the full rerun:

| Stack | Score |
| --- | ---: |
| Jackson Databind | 354.234 ns/op |
| Jackson Databind Blackbird | 334.726 ns/op |
| Serde Jackson generated | 345.968 ns/op |
| Serde Jackson runtime | 314.796 ns/op |

Current constructor serialization result from the full rerun:

| Stack | Score |
| --- | ---: |
| Jackson Databind | 3263383.436 ops/s |
| Jackson Databind Blackbird | 3457901.852 ops/s |
| Serde Jackson generated | 4126252.251 ops/s |
| Serde Jackson runtime | 3621693.071 ops/s |

GC profiling for getter/setter deserialization:

| Stack | Allocation |
| --- | ---: |
| Jackson Databind | 1072 B/op |
| Jackson Databind Blackbird | 1032 B/op |
| Serde Jackson generated | 920 B/op |
| Serde Jackson runtime | 920 B/op |

The earlier one-fork getter/setter deserialization outlier did not reproduce in
this three-fork GraalVM run. With primitive runtime setters, runtime Serde is
fastest for getter/setter deserialization at
`314.796 ns/op`, ahead of Jackson Databind Blackbird at
`334.726 ns/op` and generated Serde at
`345.968 ns/op`. Generated and runtime Serde both allocate about
`920 B/op` for this case. The generated/runtime spread now varies by
shape and reflects runtime introspection construction costs, property access,
`DerProperty` dispatch, and primitive movement rather than decode-key
dispatch alone.

A measured backend-neutral sourcegen alternative replaced nullable scalar
primitive decoders with `decodeNull()` plus primitive decoders. It preserved
behavior, but regressed the focused getter/setter generated result in local
experiments, so it is not part of the retained changes.

The remaining constructor-bound runtime Serde deserialization gap is a separate
issue: the simple runtime path no longer uses `PropertiesBag.Consumer`, but
constructor binding still pays runtime introspection construction costs that are
intentionally absent from generated Serde.

## Reproducibility Notes

- Treat single short JMH runs as directional only.
- Keep stack comparisons in the same command where practical.
- Confirm the concrete serializer/deserializer implementations in benchmark
  setup so generated and runtime paths are not accidentally mixed.
- Use async-profiler and `-prof gc` when a small runtime delta needs an
  allocation-vs-CPU explanation.
