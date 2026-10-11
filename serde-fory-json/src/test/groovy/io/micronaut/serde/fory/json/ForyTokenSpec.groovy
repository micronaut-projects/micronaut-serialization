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
import org.apache.fory.json.ForyJsonAccess
import org.apache.fory.json.reader.Utf8JsonReader
import org.apache.fory.json.resolver.JsonSharedRegistry
import org.apache.fory.json.resolver.JsonTypeResolver
import org.apache.fory.json.writer.Utf8JsonWriter
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ForyTokenSpec extends Specification {

    void "packed keys preserve first and subsequent prefixes at token boundaries"() {
        given:
        def name = 'a' * length
        def keys = Keys.create([name, 'last'])
        def writer = writer()
        def encoder = new ForyEncoder(writer)

        when:
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

        then:
        new String(writer.toJsonBytes(), StandardCharsets.UTF_8) ==
            "[{\"$name\":1,\"last\":2},{\"$name\":1,\"last\":2}]"

        where:
        length << (0..18)
    }

    void "ordered tokens and fallback accept whitespace and reversed fields"() {
        given:
        def name = 'a' * length
        def keys = Keys.create([name, 'last'])
        def fields = reversed ? ['"last"', '"' + name + '"'] : ['"' + name + '"', '"last"']
        def reader = reader("{${space}${fields[0]}${space}:${space}1,${space}${fields[1]}${space}:${space}2${space}}")
        def decoder = new ForyDecoder(reader)

        when:
        decoder.decodeObject()

        then:
        decoder.decodeKey(keys) == (reversed ? 1 : 0)
        decoder.decodeInt() == 1
        decoder.decodeKey(keys) == (reversed ? 0 : 1)
        decoder.decodeInt() == 2
        decoder.decodeKey(keys) == KeysAwareDecoder.MATCH_END_OBJECT

        when:
        decoder.finishStructure()
        reader.finish()

        then:
        noExceptionThrown()

        where:
        [length, space, reversed] << [(0..18), ['', ' \n\t\r'], [false, true]].combinations()
    }

    void "escaped and unicode names round trip through packed and raw tokens"() {
        given:
        def keys = Keys.create([name, 'tail'])
        def writer = writer()
        def encoder = new ForyEncoder(writer)
        encoder.encodeObject(Argument.OBJECT_ARGUMENT)
        encoder.encodeKey(keys, 0)
        encoder.encodeString('first')
        encoder.encodeKey(keys, 1)
        encoder.encodeNull()
        encoder.finishStructure()
        def reader = reader(new String(writer.toJsonBytes(), StandardCharsets.UTF_8))
        def decoder = new ForyDecoder(reader)

        when:
        decoder.decodeObject()

        then:
        decoder.decodeKey(keys) == 0
        decoder.decodeString() == 'first'
        decoder.decodeKey(keys) == 1
        decoder.decodeNull()
        decoder.decodeKey(keys) == KeysAwareDecoder.MATCH_END_OBJECT

        when:
        decoder.finishStructure()
        reader.finish()

        then:
        noExceptionThrown()

        where:
        name << ['"', '\\', '\n', 'é', '🍃', 'long "escaped" field name']
    }

    void "unknown fields remain available and nested keys do not lose the parent cursor"() {
        given:
        def outer = Keys.create(['child', 'last'])
        def inner = Keys.create(['value'])
        def reader = reader('{"unknown":0,"child":{"value":1},"last":2}')
        def decoder = new ForyDecoder(reader)
        decoder.decodeObject()

        expect:
        decoder.decodeKey(outer) == KeysAwareDecoder.MATCH_UNKNOWN_NAME
        decoder.decodeKey() == 'unknown'
        decoder.decodeInt() == 0
        decoder.decodeKey(outer) == 0

        when:
        decoder.decodeObject()

        then:
        decoder.decodeKey(inner) == 0
        decoder.decodeInt() == 1
        decoder.decodeKey(inner) == KeysAwareDecoder.MATCH_END_OBJECT

        when:
        decoder.finishStructure()

        then:
        decoder.decodeKey(outer) == 1
        decoder.decodeInt() == 2
        decoder.decodeKey(outer) == KeysAwareDecoder.MATCH_END_OBJECT

        when:
        decoder.finishStructure()
        reader.finish()

        then:
        noExceptionThrown()
    }

    private static Utf8JsonWriter writer() {
        def config = ForyJsonAccess.config(ForyJson.builder().build())
        new Utf8JsonWriter(config, new JsonTypeResolver(new JsonSharedRegistry(config)), new byte[16])
    }

    private static Utf8JsonReader reader(String json) {
        def config = ForyJsonAccess.config(ForyJson.builder().build())
        new Utf8JsonReader(config, new JsonTypeResolver(new JsonSharedRegistry(config)))
            .reset(json.getBytes(StandardCharsets.UTF_8), new byte[64])
    }
}
