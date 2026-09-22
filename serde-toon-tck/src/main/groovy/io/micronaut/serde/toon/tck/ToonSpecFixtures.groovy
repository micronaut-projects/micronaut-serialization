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

import groovy.json.JsonSlurper

/**
 * Loads the vendored {@code toon-format/spec} conformance fixtures (see
 * {@code spec-fixtures/README.md}) and filters out the ones that test a
 * behavior this project's {@link io.micronaut.serde.toon.util.ToonDecoder}
 * deliberately doesn't implement.
 *
 * <h2>What's excluded from decode fixtures, and why</h2>
 *
 * <p><b>Non-strict-mode-only leniencies.</b> This decoder always operates
 * in strict mode (see {@code ToonDocumentParser}'s own Javadoc) - there is
 * no configuration to relax it. A fixture with {@code options.strict:
 * false} describes behavior specific to a mode this decoder doesn't have,
 * so it's excluded - unless the fixture itself expects an error, in which
 * case strict mode (a superset of restrictions over non-strict) must also
 * reject it, so the fixture still applies.</p>
 *
 * <p><b>A fixed or configured indent size.</b> This decoder infers its
 * indent size from the document's own first nesting transition rather than
 * validating it against a fixed default (or a configured one) - a
 * deliberate design choice, covered by its own regression test ("indent is
 * inferred, not configured"). Every fixture that sets {@code
 * options.indentSize} tests exactly that unsupported configuration
 * knob, so all of them are excluded. One further fixture in
 * {@code indentation-errors.json} - "throws on depth jump of more than one
 * level" - depends on the same fixed-default-of-2 assumption without
 * declaring it as an {@code options.indentSize}: its input is a single,
 * isolated 4-space transition that this decoder legitimately infers as the
 * document's own indent size (matching the already-shipped, tested
 * behavior for a consistently-4-space-indented document), so it's excluded
 * by name in {@link #KNOWN_INDENT_DIVERGENCES}.</p>
 *
 * <p>Encode fixtures need no such filtering: {@code delimiter} and {@code
 * indentSize} are both genuinely configurable on the encode side, applied
 * via {@code options}, and there is no encode-side {@code strict} concept
 * or {@code shouldError} case in the vendored set.</p>
 */
final class ToonSpecFixtures {

    private static final List<String> DECODE_FILES = [
        'arrays-nested', 'arrays-primitive', 'arrays-tabular', 'blank-lines', 'comments',
        'delimiters', 'indentation-errors', 'numbers', 'objects-keyed', 'objects',
        'primitives', 'root-form', 'validation-errors', 'whitespace',
    ]

    private static final List<String> ENCODE_FILES = [
        'arrays-nested', 'arrays-objects', 'arrays-primitive', 'arrays-tabular', 'delimiters',
        'objects-keyed', 'objects', 'primitives', 'whitespace',
    ]

    private static final Set<String> KNOWN_INDENT_DIVERGENCES = [
        'indentation-errors.json :: throws on depth jump of more than one level',
    ] as Set<String>

    // groovy-json's own integer-literal parser (NumberValue/CharScanner,
    // still true as of groovy-json 5.0.8) accumulates digits into a raw
    // `long` with no overflow check, silently wrapping modulo 2^64 for a
    // literal wider than Long range instead of promoting to BigInteger -
    // unlike this fixture loader's own handling of the resulting value,
    // this happens before the value even reaches this class, while
    // parsing the fixture file itself. Not a TOON conformance issue; the
    // one fixture wide enough to trigger it is excluded here rather than
    // fought with a different JSON library for a single vendored value.
    private static final Set<String> KNOWN_LOADER_NUMBER_OVERFLOWS = [
        'primitives.json :: encodes large number',
    ] as Set<String>

    private ToonSpecFixtures() {
    }

    static List<ToonSpecFixture> decodeCases() {
        loadAll('decode', DECODE_FILES).findAll { applicableToDecode(it) }
    }

    static List<ToonSpecFixture> encodeCases() {
        loadAll('encode', ENCODE_FILES).findAll { !KNOWN_LOADER_NUMBER_OVERFLOWS.contains(it.toString()) }
    }

    private static boolean applicableToDecode(ToonSpecFixture fixture) {
        Map<String, Object> options = fixture.options ?: [:]

        if (options.containsKey('indentSize')) {
            return false
        }
        if (KNOWN_INDENT_DIVERGENCES.contains(fixture.toString())) {
            return false
        }
        if (options.get('strict') == false && !fixture.shouldError) {
            return false
        }

        true
    }

    private static List<ToonSpecFixture> loadAll(String category, List<String> files) {
        files.collectMany { file -> loadFile(category, file) }
    }

    private static List<ToonSpecFixture> loadFile(String category, String file) {
        String path = "/io/micronaut/serde/toon/tck/spec-fixtures/${category}/${file}.json"
        InputStream stream = ToonSpecFixtures.getResourceAsStream(path)
        if (stream == null) {
            throw new IllegalStateException("Fixture resource not found on the classpath: ${path}")
        }
        Map data = (Map) new JsonSlurper().parse(stream)
        ((List) data.tests).collect { Map t ->
            new ToonSpecFixture(
                file: "${file}.json",
                category: category,
                name: (String) t.name,
                input: plainify(t.input),
                expected: plainify(t.expected),
                shouldError: t.shouldError == true,
                options: (Map<String, Object>) plainify(t.options) ?: [:],
                note: (String) t.note
            )
        }
    }

    /**
     * Recursively replaces every {@code groovy.json.internal.LazyMap} (and
     * its nested {@code List}s) that {@link JsonSlurper} produces with a
     * plain {@link LinkedHashMap}/{@link ArrayList}. {@code LazyMap} builds
     * its backing storage from parallel key/value arrays and only
     * materializes a real {@link Map} once one is needed - iterating it
     * (via {@code each}/{@code collect}, as this method does) works fine,
     * but keyed lookups (bracket access, {@code Map.get}) on the fixtures'
     * own nested maps were observed to intermittently return an empty
     * result for a key that iteration finds populated. This normalization
     * sidesteps whatever in that lazy-build path causes it, by never
     * handing a {@code LazyMap} to the comparison logic in the first place.
     */
    private static Object plainify(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>()
            // Map.put(k, v), not the result[k] = v bracket form: for a key
            // like "properties" that collides with a reflective Groovy
            // property name, bracket assignment on a LinkedHashMap resolves
            // through property-setter lookup and throws
            // ReadOnlyPropertyException instead of storing an entry.
            value.each { k, v -> result.put((String) k, plainify(v)) }
            return result
        }
        if (value instanceof List) {
            return value.collect { plainify(it) }
        }
        return value
    }
}
