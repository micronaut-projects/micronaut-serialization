# Serialization Benchmarks Runbook

This module contains JMH microbenchmarks for serialization performance.

## Benchmarks in scope

- `io.micronaut.serde.StartupBenchmark`
- `io.micronaut.serde.SourceGenRoutingBenchmark`

`SourceGenRoutingBenchmark` compares:

- generated simple-shape routing (`GeneratedShape`)
- introspection fallback routing (`IntrospectionFallbackShape` with `@JsonAnyGetter`)

for both throughput-oriented and average-time-oriented operations.

## Run

From repository root:

```bash
./gradlew :micronaut-benchmarks:jmh
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

## Reproducibility notes

- Use the same JDK/version and machine profile for comparisons.
- Avoid running other heavy processes during benchmark execution.
- Keep benchmark inputs constant between runs.
