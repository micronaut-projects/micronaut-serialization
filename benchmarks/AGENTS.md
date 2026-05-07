# Benchmark Maintenance Guide

This directory contains JMH benchmarks and the benchmark README.

## Publishing Benchmark Results

When adding or refreshing benchmark results, always update all of these together:

- the result table in `benchmarks/README.md`
- the result table in `src/main/docs/guide/introduction/why.adoc`
- the checked-in README chart in `benchmarks/user-bean-benchmark-results.svg`
- the checked-in user-guide chart in `src/main/docs/resources/img/user-bean-benchmark-results.svg`

Always include the run environment and JMH settings near the results:

- JDK version
- fork count
- warmup iteration count
- measurement iteration count
- iteration duration
- result units

## README Result Table

In `benchmarks/README.md`, put local results in the `Latest Local Results` section as a Markdown table with this format:

```markdown
| Benchmark | Stack | Score |
| --- | --- | ---: |
| `serialize` | Jackson Databind | 386245.261 ops/s |
| `serialize` | Jackson Databind Blackbird | 384001.553 ops/s |
| `serialize` | Serde Jackson generated | 369538.854 ops/s |
| `serialize` | Serde Jackson runtime | 311175.518 ops/s |
```

Use the exact JMH score precision in the README table. Keep the benchmark names in code formatting and include units in every score cell.

## User Guide Result Table

In `src/main/docs/guide/introduction/why.adoc`, present the same result set as an AsciiDoc table for documentation readers:

```asciidoc
|===
| Operation | Jackson Databind | Jackson Databind Blackbird | Micronaut Serialization generated | Micronaut Serialization runtime

| Serialize throughput
| 386,245 ops/s
| 384,002 ops/s
| 369,539 ops/s
| 311,176 ops/s
|===
```

Round guide values to whole numbers, use thousands separators, and include units in each value. Keep the guide table synchronized with the README result table, and keep the guide narrative short and focused on the meaningful comparison.

After the guide table, include the same benchmark chart that appears in the README:

```asciidoc
image::user-bean-benchmark-results.svg[UserBeanSerdeBenchmark local results]
```

## SVG Chart Format

When publishing benchmark results, include a checked-in SVG chart next to the README and an equivalent checked-in copy for the user guide. Reference the README chart with a relative Markdown image link and the user-guide chart with the AsciiDoc image macro shown above.

Use this format:

- File name: `user-bean-benchmark-results.svg` for `UserBeanSerdeBenchmark` results.
- README location: `benchmarks/user-bean-benchmark-results.svg`.
- User-guide location: `src/main/docs/resources/img/user-bean-benchmark-results.svg`.
- Canvas: static inline SVG, no external scripts, fonts, images, or network dependencies.
- Layout: one horizontal bar-chart panel per metric family.
- Panels: serialization throughput, deserialize average time, and round-trip average time.
- Axis: each panel uses its own x-axis starting at zero.
- Axis scale: choose a rounded maximum that is greater than or equal to the largest value in that panel.
- Axis ticks: show zero, midpoint, and maximum tick labels.
- Axis labels: include the unit and direction in the panel title, for example `ops/s, higher is better` or `ns/op, lower is better`.
- Bars: keep stack ordering consistent across panels: Jackson Databind, Jackson Databind Blackbird, Serde Jackson generated, Serde Jackson runtime.
- Values: show rounded display values with thousands separators at the end of each bar.
- Color mapping: keep a stable color per stack across all panels.
- Accessibility: include a `<title>` and `<desc>` that describe the benchmark and compared stacks.

Do not put throughput and average-time results on the same axis. Longer bars mean faster only in the throughput chart; average-time charts still show measured duration, where lower values are better.

## Validation

After changing the chart, run:

```bash
xmllint --noout benchmarks/user-bean-benchmark-results.svg src/main/docs/resources/img/user-bean-benchmark-results.svg
```

After adding or changing benchmark documentation files, run:

```bash
./gradlew -q spotlessCheck
```
