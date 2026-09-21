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
package io.micronaut.serde.support.patch

import io.micronaut.serde.patch.JsonPatchOptions
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.config.CoercionPolicy
import spock.lang.Specification

import java.nio.file.Files

class ReplayStoreSpec extends Specification {
    def 'replay budget is shared by all live tapes and is released on close'() {
        given:
        def scope = new ReplayStore.Scope(new JsonPatchOptions(8192, 100000, 100, 1000, null, true))
        def first = new ReplayStore(scope)
        def second = new ReplayStore(scope)
        first.write(PatchToken.STRING, 'one')

        when:
        second.write(PatchToken.STRING, 'two')

        then:
        thrown(IOException)
        scope.memory == 8192

        when:
        first.close()
        second.write(PatchToken.STRING, 'two')

        then:
        scope.memory == 8192

        cleanup:
        scope.close()
        assert scope.memory == 0
        assert scope.bytes == 0
    }

    def 'buffered decoders share the scope preserve coercion and close unread tapes'() {
        given:
        def directory = Files.createTempDirectory('json-patch-decoder-')
        def scope = new ReplayStore.Scope(new JsonPatchOptions(0, 1000000, 100, 1000, directory, true))
        def store = new ReplayStore(scope)
        store.write(PatchToken.START_ARRAY, '')
        store.write(PatchToken.STRING, '42')
        store.write(PatchToken.START_OBJECT, '')
        store.write(PatchToken.KEY, 'a')
        store.write(PatchToken.NUMBER, '1')
        store.write(PatchToken.END_OBJECT, '')
        store.write(PatchToken.END_ARRAY, '')
        def decoder = new PatchedDecoder(store.reader(), LimitingStream.DEFAULT_LIMITS, CoercionPolicy.STRICT, scope, null)
        def array = decoder.decodeArray()
        def scalar = array.decodeBuffer()
        def abandoned = array.decodeBuffer()
        array.finishStructure()

        expect:
        scalar.coercionPolicy == CoercionPolicy.STRICT

        when:
        scalar.decodeInt()

        then:
        thrown(IOException)

        when:
        scope.close()

        then:
        scope.bytes == 0
        scope.memory == 0
        try (def files = Files.list(directory)) {
            assert files.count() == 0
        }

        cleanup:
        scope.close()
        Files.delete(directory)
    }

    def 'finishing an array consumes remaining nested values'() {
        given:
        def scope = new ReplayStore.Scope(JsonPatchOptions.DEFAULT)
        def store = new ReplayStore(scope)
        store.write(PatchToken.START_ARRAY, '')
        store.write(PatchToken.NUMBER, '1')
        store.write(PatchToken.START_ARRAY, '')
        store.write(PatchToken.NUMBER, '2')
        store.write(PatchToken.END_ARRAY, '')
        store.write(PatchToken.END_ARRAY, '')
        def decoder = new PatchedDecoder(store.reader(), LimitingStream.DEFAULT_LIMITS, CoercionPolicy.LENIENT, scope, null)

        when:
        decoder.decodeArray().finishStructure(true)

        then:
        noExceptionThrown()

        cleanup:
        scope.close()
    }

    def 'large unicode values replay independently across a spill boundary'() {
        given:
        def directory = Files.createTempDirectory('json-patch-replay-')
        def scope = new ReplayStore.Scope(new JsonPatchOptions(8192, 1000000, 100, 1000, directory, true))
        def store = new ReplayStore(scope)
        String value = ('λ😀' * 30000) + '\uD800'
        store.write(PatchToken.STRING, value)
        store.write(PatchToken.NULL, '')

        expect:
        scope.memory == 0
        (1..2).every {
            try (def reader = store.reader()) {
                assert reader.current() == PatchToken.STRING
                assert reader.text() == value
                reader.next()
                assert reader.current() == PatchToken.NULL
                reader.next()
                assert reader.current() == null
            }
            true
        }

        cleanup:
        scope.close()
        try (def files = Files.list(directory)) {
            assert files.count() == 0
        }
        Files.delete(directory)
    }
}
