# java-json-benchmark: Micronaut Serde comparison

This directory captures a Micronaut Serialization comparison run inside
[fabienrenaud/java-json-benchmark](https://github.com/fabienrenaud/java-json-benchmark)
(upstream commit `e0135e5`). The upstream harness is not vendored here; the
patch adds one library, `MICRONAUT`, to it.

- `micronaut-serde.patch`: the harness changes, against upstream `e0135e5`.
- `results/serialize-users-1kb.txt`, `results/deserialize-users-1kb.txt`: raw JMH output.

## What the patch does

- Adds `Library.MICRONAUT` and registers it for the `users` data type, `databind` API.
- Annotates the shared `Users` model and its nested `User` and `Friend` classes with
  `@Serdeable`, so Micronaut generates serializers and deserializers at build time.
  Other libraries ignore the annotation.
- Builds a `MicronautMapper<Users>` once, from an `ApplicationContext` and
  `JacksonJsonMapper.createSpecific(...)`. The generated serde is used, not the runtime fallback.
- Adds `micronaut()` benchmark methods to `Serialization` and `Deserialization`.
- Adds a `micronaut` round-trip test to `JsonBenchmark`, plus a `byte[]` branch in its
  `test(...)` helper. The Users serialization and deserialization suites pass (34 tests each).
- Depends on Micronaut Serde `3.3.0-SNAPSHOT` from `mavenLocal` and Micronaut `5.2.13`.

## Reproduce

From this repository, publish the Micronaut Serde modules the harness uses:

```bash
./gradlew :micronaut-serde-api:publishToMavenLocal \
  :micronaut-serde-support:publishToMavenLocal \
  :micronaut-serde-jackson:publishToMavenLocal \
  :micronaut-serde-processor:publishToMavenLocal
```

Then apply the patch to a checkout of the upstream harness and run the benchmark (JDK 25):

```bash
git clone https://github.com/fabienrenaud/java-json-benchmark
cd java-json-benchmark && git checkout e0135e5
git apply /path/to/micronaut-serialization/benchmarks/java-json-benchmark/micronaut-serde.patch
./gradlew shadowJar
export SEED=42
# -f/-wi/-i/-m are global options and must come before the subcommand
java -jar build/libs/app.jar -f 2 -wi 3 -i 5 -m 2 ser   --apis databind --datatype users --number 1 --size 1 \
  --libs micronaut,jackson,jackson_afterburner,jackson_blackbird,gson,genson,fastjson,dsljson,avajejsonb,foryjson
java -jar build/libs/app.jar -f 2 -wi 3 -i 5 -m 2 deser --apis databind --datatype users --number 1 --size 1 \
  --libs micronaut,jackson,jackson_afterburner,jackson_blackbird,gson,genson,fastjson,dsljson,avajejsonb,foryjson
```

## Environment and JMH settings

- JDK: Temurin OpenJDK 25.0.4.1 (`-server -Xms2g -Xmx2g`, `jdk.incubator.vector`)
- Machine: 4 vCPU Intel Xeon @ 2.10 GHz VM. This is not the upstream EC2 `c8g.xlarge`, so absolute
  numbers are not comparable with the upstream published results.
- Forks: 2. Warmup: 3 iterations × 10 s. Measurement: 5 iterations × 2 s. Threads: 3.
- Mode: throughput (`ops/s`, higher is better). Payload: one random `Users` document, about 1 KB, seed 42.
- Micronaut Serde at repository commit `9ca9dbe` (`3.3.0-SNAPSHOT`), Micronaut `5.2.13`, Jackson 3 (`tools.jackson`).
  The other libraries use the versions pinned by the upstream harness (Jackson `2.17.1`).

## Results: `Users`, 1 KB, databind

Scores are ops/s with the JMH 99.9% confidence error, sorted fastest first.

**Serialize**

| Benchmark | Stack | Score |
| --- | --- | ---: |
| `serialize` | Fory JSON | 2,889,146 ± 725,529 ops/s |
| `serialize` | fastjson2 | 2,793,378 ± 355,382 ops/s |
| `serialize` | fastjson2 (features) | 2,487,588 ± 715,139 ops/s |
| `serialize` | DSL-JSON | 1,557,607 ± 82,607 ops/s |
| `serialize` | Avaje JSON-B (Jackson adapter) | 1,337,944 ± 152,230 ops/s |
| `serialize` | Jackson Databind + Afterburner | 1,244,657 ± 170,703 ops/s |
| `serialize` | Jackson Databind + Blackbird | 1,233,481 ± 159,284 ops/s |
| `serialize` | Avaje JSON-B | 1,231,587 ± 89,575 ops/s |
| `serialize` | Jackson Databind | 1,202,440 ± 65,580 ops/s |
| `serialize` | **Micronaut Serde (generated)** | **1,077,938 ± 85,595 ops/s** |
| `serialize` | Genson | 876,944 ± 113,997 ops/s |
| `serialize` | Gson | 854,585 ± 79,503 ops/s |
| `serialize` | DSL-JSON (reflection) | 824,393 ± 240,904 ops/s |

**Deserialize**

| Benchmark | Stack | Score |
| --- | --- | ---: |
| `deserialize` | Fory JSON | 3,027,600 ± 194,134 ops/s |
| `deserialize` | fastjson2 (features) | 2,540,045 ± 121,849 ops/s |
| `deserialize` | fastjson2 | 2,481,628 ± 153,044 ops/s |
| `deserialize` | DSL-JSON | 1,397,687 ± 244,962 ops/s |
| `deserialize` | Avaje JSON-B | 1,333,206 ± 254,997 ops/s |
| `deserialize` | **Micronaut Serde (generated)** | **1,127,372 ± 86,652 ops/s** |
| `deserialize` | Jackson Databind + Blackbird | 933,954 ± 57,092 ops/s |
| `deserialize` | Jackson Databind + Afterburner | 924,570 ± 75,225 ops/s |
| `deserialize` | Avaje JSON-B (Jackson adapter) | 871,672 ± 94,345 ops/s |
| `deserialize` | DSL-JSON (reflection) | 866,033 ± 225,391 ops/s |
| `deserialize` | Jackson Databind | 698,801 ± 61,585 ops/s |
| `deserialize` | Genson | 573,044 ± 33,159 ops/s |
| `deserialize` | Gson | 495,150 ± 45,289 ops/s |

## Reading the numbers

- **Deserialization:** Micronaut is about 1.6× Jackson Databind (1.13M vs 0.70M ops/s) and about 1.2×
  Jackson with Blackbird or Afterburner. The confidence intervals do not overlap, so this gap is real for this run.
- **Serialization:** Micronaut is about 10% below Jackson Databind (1.08M vs 1.20M ops/s). The intervals
  overlap slightly, so treat this as roughly on par rather than a measured regression.
- Fory JSON and fastjson2 lead both directions by roughly 2–2.7× over Micronaut.

## Caveats

- Micronaut here runs on Jackson 3 (`tools.jackson`) via Micronaut 5. The Jackson rows use Jackson 2.17.1.
  This compares the stacks as shipped, not a like-for-like Jackson version.
- Only the generated serde path was measured. The runtime fallback was not measured.
- Encoded sizes were not compared across libraries. Micronaut was configured with
  `micronaut.serde.serialization.inclusion=ALWAYS`, matching the repository's `FormatComparisonBenchmark`.
- Only the `Users` 1 KB payload, `databind` API, and the subset of libraries listed above were run. The `Clients`
  model is not wired for Micronaut (its nested types have no `@Serdeable`).
- Single run on a shared 4-vCPU VM. Several error bands are ±15–25%.
