# Serialization Benchmarks Runbook

This module contains JMH microbenchmarks for serialization performance.

## Benchmarks in scope

- `io.micronaut.serde.StartupBenchmark`
- `io.micronaut.serde.SourceGenRoutingBenchmark`
- `io.micronaut.serde.UserBeanSerdeBenchmark`

`SourceGenRoutingBenchmark` compares:

- generated simple-shape routing (`GeneratedShape`)
- introspection fallback routing (`IntrospectionFallbackShape` with `@JsonAnyGetter`)

for both throughput-oriented and average-time-oriented operations.

`UserBeanSerdeBenchmark` compares a `Users` payload modeled after the
`fabienrenaud/java-json-benchmark` users data shape. The payload includes nested user
objects, friend objects, arrays/lists, strings, numbers, and booleans. The compared
stacks are:

- Jackson Databind
- Jackson Databind with Blackbird
- Micronaut Serde Jackson with generated serdes
- Micronaut Serde Jackson with generated serdes disabled

## Run

From repository root:

```bash
./gradlew :micronaut-benchmarks:jmh
```

The full task uses the JMH defaults configured in `benchmarks/build.gradle`. To run
only the user-bean comparison with a short local sanity-check profile:

```bash
./gradlew -q :micronaut-benchmarks:jmhJar
repo=$PWD
tmpdir=$(mktemp -d /tmp/mn-jmh.XXXXXX)
cd "$tmpdir"
jar xf "$repo/benchmarks/build/libs/micronaut-benchmarks-3.0.0-SNAPSHOT-jmh.jar"
java -cp "$tmpdir:$tmpdir/*" org.openjdk.jmh.Main '.*UserBeanSerdeBenchmark.*' -wi 0 -i 1 -f 1 -r 1s -tu ns
```

## Current JMH configuration

Configured in `benchmarks/build.gradle`:

- `fork = 10`
- `iterations = 1`
- `warmupIterations = 0`

These are intentionally lightweight defaults for CI/dev feedback. For deeper local analysis,
increase warmup/measurement iterations and keep multiple forks.

## Interpreting results

- `Mode.Throughput`: higher ops/sec is better.
- `Mode.AverageTime`: lower per-operation time is better.

Compare generated vs fallback benchmarks under the same mode and environment.
The Serde stacks resolve the root `Users` mapper during JMH setup and validate
the serializer/deserializer classes retrieved from `SerdeRegistry`. The default
Serde stack asserts the generated `SerdeUsersSerializer` and
`SerdeUsersDeserializer`. They use `ALWAYS` serialization inclusion to match
Jackson's default include-all behavior. The runtime fallback validation unwraps
the error-catching layer and asserts `SimpleObjectSerializer` and
`SimpleObjectDeserializer`.

## Latest Local Results

The following results were captured on JDK 25 with 3 forks, 5 warmup iterations,
and 10 measurement iterations with 1-second iterations.

| Benchmark | Stack | Score |
| --- | --- | ---: |
| `serialize` | Jackson Databind | 389165.759 ops/s |
| `serialize` | Jackson Databind Blackbird | 389564.438 ops/s |
| `serialize` | Serde Jackson generated | 510335.143 ops/s |
| `serialize` | Serde Jackson runtime | 414884.942 ops/s |
| `deserialize` | Jackson Databind | 3293.933 ns/op |
| `deserialize` | Jackson Databind Blackbird | 3320.455 ns/op |
| `deserialize` | Serde Jackson generated | 3060.043 ns/op |
| `deserialize` | Serde Jackson runtime | 3558.997 ns/op |
| `roundTrip` | Jackson Databind | 6545.609 ns/op |
| `roundTrip` | Jackson Databind Blackbird | 6527.765 ns/op |
| `roundTrip` | Serde Jackson generated | 5112.827 ns/op |
| `roundTrip` | Serde Jackson runtime | 6095.837 ns/op |

![UserBeanSerdeBenchmark local results](user-bean-benchmark-results.svg)

Summary:

- Serde Jackson generated had the highest serialization throughput, about
  31.1% ahead of Jackson Databind and about 23.0% ahead of runtime Serde
  serialization.
- Serde Jackson generated was the fastest deserialization path in this run,
  about 7.1% faster than Jackson Databind.
- Serde Jackson generated was the fastest round-trip path, about 21.9% faster
  than Jackson Databind.
- Serde Jackson runtime serialization was about 6.6% ahead of Jackson Databind,
  but runtime Serde remained slower than generated Serde across all measured
  operations.

A serialization GC-profiler run showed the generated Serde byte-array path at
about `6192 B/op`, down from about `22321 B/op` before releasing the Jackson
`BufferRecycler` acquired by `JacksonJsonMapper.writeValueAsBytes`. Jackson
Databind measured about `6176 B/op` in the same run.

## Reproducibility notes

- Use the same JDK/version and machine profile for comparisons.
- Avoid running other heavy processes during benchmark execution.
- Keep benchmark inputs constant between runs.
