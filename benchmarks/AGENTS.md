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

In `benchmarks/README.md`, put local results in the `User Bean Results` section as a Markdown table with this format:

```markdown
| Benchmark | Stack | Score |
| --- | --- | ---: |
| `serialize` | Jackson Databind | 386554.197 ops/s |
| `serialize` | Jackson Databind Blackbird | 389268.121 ops/s |
| `serialize` | Serde Jackson generated | 553139.927 ops/s |
| `serialize` | Serde Jackson runtime | 418341.808 ops/s |
| `serialize` allocation | Jackson Databind | 6176.018 B/op |
```

Use the exact JMH score precision in the README table. Keep the benchmark names in code formatting and include units in every score cell. Include the `serialize` allocation rows (`gc.alloc.rate.norm` from `-prof gc`) so the table matches the chart's allocation panel.

## User Guide Result Table

In `src/main/docs/guide/introduction/why.adoc`, present the same result set as an AsciiDoc table for documentation readers:

```asciidoc
|===
| Operation | Jackson Databind | Jackson Databind Blackbird | Micronaut Serialization generated | Micronaut Serialization runtime

| Serialize throughput
| 386,554 ops/s
| 389,268 ops/s
| 553,140 ops/s
| 418,342 ops/s

| Serialize allocation
| 6,176 B/op
| 6,176 B/op
| 2,968 B/op
| 2,990 B/op
|===
```

Round guide values to whole numbers, use thousands separators, and include units in each value. Include the serialize-allocation row so the guide table matches the chart's allocation panel. Keep the guide table synchronized with the README result table, and keep the guide narrative short and focused on the meaningful comparison.

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
- Panels: serialization throughput, deserialize average time, round-trip average time, and serialization allocation (`B/op` from `-prof gc`, lower is better).
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
