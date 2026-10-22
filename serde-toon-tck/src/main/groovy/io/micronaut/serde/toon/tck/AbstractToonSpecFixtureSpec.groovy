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

import io.micronaut.core.type.Argument
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Runs the language-agnostic fixtures vendored from the {@code
 * toon-format/spec} repository (see {@code spec-fixtures/README.md})
 * through whichever {@link ToonSpec} implementation the concrete subclass
 * mixes in - this project's own TOON mapper, or (in the jtoon test suite)
 * jtoon itself.
 *
 * <p>Unlike the rest of this TCK, which encodes this codebase's own
 * hand-written expectations, these fixtures are an independent conformance
 * signal against the specification itself, maintained outside this
 * project. See {@link ToonSpecFixtures} for exactly which fixtures are
 * excluded and why - mainly, cases that depend on decoder behavior this
 * project's decoder deliberately doesn't implement (non-strict mode; a
 * fixed or configured indent size).</p>
 */
abstract class AbstractToonSpecFixtureSpec extends Specification implements ToonSpec {

    @Unroll
    void "decode fixture: #fixture"() {
        when:
        Exception caught = null
        Object result = null
        try {
            result = readToon(fixture.input as String, Argument.of(Object))
        } catch (Exception e) {
            caught = e
        }

        then:
        if (fixture.shouldError) {
            assert caught != null: "expected an error decoding: ${fixture.input}"
        } else {
            assert caught == null: "unexpected error decoding: ${fixture.input} (${caught})"
            assert ToonSpecFixtures.jsonEquals(result, fixture.expected): "decoded ${result} but expected ${fixture.expected}"
        }

        where:
        fixture << ToonSpecFixtures.decodeCases()
    }

    @Unroll
    void "encode fixture: #fixture"() {
        given:
        Map<String, Object> properties = encodeProperties(fixture.options ?: [:])

        when:
        String actual = properties ? writeToonWithProperties(properties, fixture.input) : writeToon(fixture.input)

        then:
        actual == fixture.expected

        where:
        fixture << ToonSpecFixtures.encodeCases()
    }

    private static Map<String, Object> encodeProperties(Map<String, Object> options) {
        Map<String, Object> properties = [:]
        if (options.delimiter) {
            properties['micronaut.serde.format.toon.delimiter'] = options.delimiter
        }
        if (options.indentSize) {
            properties['micronaut.serde.format.toon.indent'] = options.indentSize
        }
        properties
    }
}
