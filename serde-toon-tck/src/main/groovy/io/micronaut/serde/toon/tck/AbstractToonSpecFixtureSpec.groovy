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
            assert jsonEquals(result, fixture.expected): "decoded ${result} but expected ${fixture.expected}"
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

    /**
     * Structural JSON equality, tolerant of the actual side's map/list
     * implementation and of a scalar number's exact Java type (an
     * implementation may reasonably decode {@code 42} as a {@code Long}
     * where the fixture's own JSON parsing produced an {@code Integer}).
     */
    private static boolean jsonEquals(Object actual, Object expected) {
        if (expected == null) {
            return actual == null
        }
        if (expected instanceof Map) {
            if (!(actual instanceof Map)) {
                return false
            }
            Map<?, ?> e = (Map) expected
            Map<?, ?> a = (Map) actual
            if (a.keySet() != e.keySet()) {
                return false
            }
            // Map.get(k), not the a[k] bracket form: for a key like
            // "properties" that collides with a reflective Groovy property
            // name, bracket access on certain Map instances resolves
            // through property-getter lookup instead of a real entry
            // lookup and silently returns the wrong value.
            return e.every { k, v -> jsonEquals(a.get(k), v) }
        }
        if (expected instanceof List) {
            if (!(actual instanceof List)) {
                return false
            }
            List<?> e = (List) expected
            List<?> a = (List) actual
            if (a.size() != e.size()) {
                return false
            }
            return (0..<e.size()).every { i -> jsonEquals(a[i], e[i]) }
        }
        if (expected instanceof Number) {
            return actual instanceof Number && new BigDecimal(expected.toString()) == new BigDecimal(actual.toString())
        }
        actual == expected
    }
}
