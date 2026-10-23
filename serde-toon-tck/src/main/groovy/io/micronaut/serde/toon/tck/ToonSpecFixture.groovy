/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.toon.tck

/**
 * One test case from a vendored {@code toon-format/spec} fixture file (see
 * {@code spec-fixtures/README.md}).
 *
 * <p>{@code input}/{@code expected} are whatever the fixture's own JSON put
 * there: for a decode case, {@code input} is the TOON source text (a
 * {@link String}) and {@code expected} is the JSON value it should decode
 * to (a {@link Map}/{@link List}/scalar, or {@code null} when {@code
 * shouldError}); for an encode case, {@code input} is the JSON value to
 * encode and {@code expected} is the TOON text it should produce.</p>
 */
class ToonSpecFixture {
    String file
    String category
    String name
    Object input
    Object expected
    boolean shouldError
    Map<String, Object> options
    String note

    @Override
    String toString() {
        "${file} :: ${name}"
    }
}
