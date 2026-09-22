# JSON Patch conformance fixtures

`tests.json` and `spec_tests.json` are unmodified copies from
[json-patch/json-patch-tests](https://github.com/json-patch/json-patch-tests/tree/2a928f9044aad35c74e2788d498bcf2c6b91adea),
pinned at commit `2a928f9044aad35c74e2788d498bcf2c6b91adea`.
The [upstream README](https://github.com/json-patch/json-patch-tests/blob/2a928f9044aad35c74e2788d498bcf2c6b91adea/README.md)
contains the test format, credits, and Apache-2.0 license notice reproduced below.
This is a community conformance corpus, not an IETF certification suite.

`AbstractJsonPatchSpec` loads these resources through serde. Jackson and JSON-P
run each case with streaming output, validated output, and materialized results,
each with memory-only replay and with a zero-byte replay memory budget plus
file spill. Error cases must throw an IOException; upstream error descriptions
are not matched against implementation-specific messages. Validated output must
remain empty on failure. Every spill directory must be empty after the operation.

The pinned corpus has 112 cases: 108 enabled and four disabled upstream. Disabled
cases appear as skipped iterations in the test reports. There are no additional
local exclusions. Comment-only records are ignored. Cases without `expected`
assert success only; an explicit `expected: null` still asserts a null result.

The disabled cases (one-based indexes) are:

- `tests.json[11]`: Toplevel scalar values OK?
- `tests.json[57]`: Whole document
- `tests.json[86]`: duplicate ops
- `spec_tests.json[14]`: A.13 Invalid JSON Patch Document

The existing handwritten tests independently cover these scalar/root operations
and duplicate operation members. Fixture parsing preserves nulls using JsonNode,
but round-tripping a fixture does not preserve duplicate JSON members. Keep raw
JSON regression tests for duplicate-member behavior, even if upstream changes
its disabled flags.

To update, copy both JSON files from the same upstream commit without editing
them, update this provenance and the case counts, review disabled cases and any
duplicate-member cases, then run:

```shell
./gradlew :micronaut-serde-jackson:test --tests 'io.micronaut.serde.jackson.JsonPatchSpec' :micronaut-serde-jsonp:test --tests 'io.micronaut.serde.json.stream.JsonPatchSpec'
```

Tests use the checked-in resources and do not download fixtures during the build.

## Upstream license notice

Copyright 2014 The Authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
