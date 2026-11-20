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
package io.micronaut.serde.fory.json

import io.micronaut.core.type.Argument
import io.micronaut.serde.Keys
import io.micronaut.serde.KeysAwareDecoder
import org.apache.fory.json.ForyJson
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ForyTokenSpec extends Specification {

    private static final ForyJson FORY = ForyJson.builder().build()

    void "packed keys preserve first and subsequent prefixes at token boundaries"() {
        given:
        def name = 'a' * length
        def keys = Keys.create([name, 'last'])

        when:
        def json = write { ForyEncoder encoder ->
            encoder.encodeArray()
            2.times {
                encoder.encodeObject()
                encoder.encodeKey(keys, 0)
                encoder.encodeInt(1)
                encoder.encodeKey(keys, 1)
                encoder.encodeInt(2)
                encoder.finishStructure()
            }
            encoder.finishStructure()
        }

        then:
        json == "[{\"$name\":1,\"last\":2},{\"$name\":1,\"last\":2}]"

        where:
        length << (0..18)
    }

    void "ordered tokens and fallback accept whitespace and reversed fields"() {
        given:
        def name = 'a' * length
        def keys = Keys.create([name, 'last'])
        def fields = reversed ? ['"last"', '"' + name + '"'] : ['"' + name + '"', '"last"']
        def json = "{${space}${fields[0]}${space}:${space}1,${space}${fields[1]}${space}:${space}2${space}}".toString()

        when:
        def result = read(json) { ForyDecoder decoder ->
            decoder.decodeObject()
            def r = [decoder.decodeKey(keys), decoder.decodeInt(), decoder.decodeKey(keys), decoder.decodeInt(), decoder.decodeKey(keys)]
            decoder.finishStructure()
            r
        }

        then:
        result == [reversed ? 1 : 0, 1, reversed ? 0 : 1, 2, KeysAwareDecoder.MATCH_END_OBJECT]

        where:
        [length, space, reversed] << [(0..18), ['', ' \n\t\r'], [false, true]].combinations()
    }

    void "escaped and unicode names round trip through packed and raw tokens"() {
        given:
        def keys = Keys.create([name, 'tail'])
        def json = write { ForyEncoder encoder ->
            encoder.encodeObject(Argument.OBJECT_ARGUMENT)
            encoder.encodeKey(keys, 0)
            encoder.encodeString('first')
            encoder.encodeKey(keys, 1)
            encoder.encodeNull()
            encoder.finishStructure()
        }

        when:
        def result = read(json) { ForyDecoder decoder ->
            decoder.decodeObject()
            def r = [decoder.decodeKey(keys), decoder.decodeString(), decoder.decodeKey(keys), decoder.decodeNull(), decoder.decodeKey(keys)]
            decoder.finishStructure()
            r
        }

        then:
        result == [0, 'first', 1, true, KeysAwareDecoder.MATCH_END_OBJECT]

        where:
        name << ['"', '\\', '\n', 'é', '🍃', 'long "escaped" field name']
    }

    void "unknown fields remain available and nested keys do not lose the parent cursor"() {
        given:
        def outer = Keys.create(['child', 'last'])
        def inner = Keys.create(['value'])

        when:
        def result = read('{"unknown":0,"child":{"value":1},"last":2}') { ForyDecoder decoder ->
            decoder.decodeObject()
            def r = [decoder.decodeKey(outer), decoder.decodeKey(), decoder.decodeInt(), decoder.decodeKey(outer)]
            decoder.decodeObject()
            r += [decoder.decodeKey(inner), decoder.decodeInt(), decoder.decodeKey(inner)]
            decoder.finishStructure()
            r += [decoder.decodeKey(outer), decoder.decodeInt(), decoder.decodeKey(outer)]
            decoder.finishStructure()
            r
        }

        then:
        result == [KeysAwareDecoder.MATCH_UNKNOWN_NAME, 'unknown', 0, 0,
                   0, 1, KeysAwareDecoder.MATCH_END_OBJECT,
                   1, 2, KeysAwareDecoder.MATCH_END_OBJECT]
    }

    private static String write(Closure<?> body) {
        new String(FORY.writeUtf8 { writer -> body.call(new ForyEncoder(writer)) }, StandardCharsets.UTF_8)
    }

    private static <T> T read(String json, Closure<T> body) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8)
        FORY.readUtf8(bytes, 0, bytes.length) { reader -> body.call(new ForyDecoder(reader)) }
    }
}
