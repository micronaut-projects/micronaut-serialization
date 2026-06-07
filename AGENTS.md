# Micronaut Serialization Agent Guide

This repository is a Micronaut Framework module, not an application. The root project coordinates the Gradle build, documentation, examples, tests, and publishing metadata. Do not add production code directly at the root.

## Repository Layout

- Core modules live in `serde-api`, `serde-support`, `serde-jackson`, `serde-jsonp`, `serde-bson`, `serde-oracle-jdbc-json`, and related TCK modules.
- The compile-time annotation processor lives in `serde-processor`. Follow `serde-processor/AGENTS.md` for generated-serde and Sourcegen work.
- Documentation lives under `src/main/docs/guide`, with the guide table of contents in `src/main/docs/guide/toc.yml`.
- Runnable documentation examples live under `doc-examples`.
- JMH benchmarks live in `benchmarks`. Follow `benchmarks/AGENTS.md` when changing benchmark code, benchmark results, or benchmark charts.
- Shared Gradle convention logic lives in `buildSrc`. Prefer convention plugins there over duplicating build logic in individual build files.

## Repository Skills

This repository includes local agent skills under `.agents/skills`. Use them for deeper, task-specific workflow before making related changes:

- `coding`: Java implementation, framework bug fixes, internal API evolution, JSpecify null-safety, public API review, and committer-ready verification.
- `gradle`: build failures, version catalogs, BOM/publishing/signing behavior, binary compatibility, and `micronaut-build` plugin work.
- `docs`: module guide updates under `src/main/docs/guide`, `toc.yml` changes, Micronaut docs macros, and docs build/publishing fixes.
- `guides`: standalone tutorial work for `micronaut-projects/micronaut-guides`; do not use it for ordinary module guide pages in this repository.
- `micronaut-sourcegen`: any Sourcegen-based generated source, Kotlin/Groovy-compatible source, bytecode generation, or generated serde model work.
- `agent-md-refactor`: splitting or reorganizing oversized instruction files such as `AGENTS.md`, `CLAUDE.md`, or `COPILOT.md`.
- `skill-creator`: creating, updating, validating, or packaging skills under `.agents/skills` or equivalent skill directories.

Read only the relevant `SKILL.md` first, then load referenced files from that skill's `references/` directory only when needed.

## Build And Test Commands

- Use the Gradle wrapper for all build work: `./gradlew`.
- Use quiet output for non-test Gradle tasks: `./gradlew -q <task>`.
- Do not use quiet output for test tasks because it hides useful test result output.
- Gradle project paths use standardized Micronaut names. For example, directory `serde-jackson` maps to project `:micronaut-serde-jackson`.
- Compile an affected Java/Groovy module before broad testing: `./gradlew -q :micronaut-<module>:compileTestJava :micronaut-<module>:compileTestGroovy`.
- Run targeted tests first: `./gradlew :micronaut-<module>:test --tests 'pkg.ClassTest'`.
- Run the full affected module test task before finishing: `./gradlew :micronaut-<module>:test`.
- Run aggregate Checkstyle with `./gradlew -q cM` when source changes may affect style.
- Run `./gradlew -q spotlessCheck` for new files or formatting/license-sensitive changes. If it fails, run `./gradlew -q spotlessApply` and re-run the check.
- Build docs with `./gradlew -q docs` or `./gradlew -q publishGuide` when guide content changes.

## Coding Standards

- Keep changes scoped to the affected modules and existing package boundaries.
- Preserve public API and binary compatibility unless the user explicitly approves a breaking change. Run `./gradlew -q japiCmp` when public signatures change.
- Mark non-user-facing APIs with `@io.micronaut.core.annotation.Internal`.
- Use `jakarta.inject`, not `javax.inject`.
- Do not introduce reflection-based behavior; Micronaut serialization should remain friendly to build-time analysis and GraalVM native images.
- Use JSpecify annotations such as `org.jspecify.annotations.Nullable` and `org.jspecify.annotations.NonNull` where nullness is part of the contract.
- New Java packages should include `package-info.java` with `@org.jspecify.annotations.NullMarked`; add it to existing packages you materially touch unless the local package has a deliberate exception.
- Prefer modern Java idioms already used in the codebase, including records, pattern matching, sealed types, and `var` for clear local variables.
- Avoid fully qualified class names unless they are needed to resolve a name conflict.
- Add dependencies through the Gradle version catalog in `gradle/libs.versions.toml`; do not hard-code dependency versions in module builds.

## Serialization-Specific Guidance

- Treat serializers and deserializers as runtime hot-path code. Avoid adding repeated annotation lookups, feature checks, or serde discovery inside per-property positive paths.
- Prefer computing configuration, feature flags, property arguments, serializers, and deserializers once during serde creation or construction and storing them in final fields.
- Keep generated and runtime serde behavior aligned. When changing generated serde behavior, include tests that compare generated behavior with the runtime fallback and Jackson Databind where practical.
- Use Micronaut Sourcegen APIs for generated source work. Follow the scoped `serde-processor/AGENTS.md` instructions before changing Sourcegen-based cserdes.

## Documentation

- Write user guide content in AsciiDoc under `src/main/docs/guide`.
- Update `src/main/docs/guide/toc.yml` when adding or reorganizing guide pages.
- Prefer runnable examples from `doc-examples` over untested snippets.
- Prefer Micronaut docs macros such as `dependency:`, `snippet::`, `[configuration]` blocks, and generated configuration property includes over manually duplicated dependency blocks, source snippets, or property tables.
- Keep docs focused on Micronaut Serialization usage, compatibility, configuration, and migration behavior.
- Use clear AsciiDoc structure, admonitions, and descriptive image alt text.

## Workflow Expectations

- Read the relevant module build files and nearby tests before changing implementation.
- Add or update tests with the narrowest scope that proves the behavior change.
- Check for existing user changes before editing and do not revert unrelated work.
- Before finishing, report the exact verification commands run and any checks that were skipped.
