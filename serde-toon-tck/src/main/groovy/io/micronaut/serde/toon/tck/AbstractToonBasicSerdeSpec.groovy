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

import java.nio.charset.StandardCharsets

/**
 * Validates basic record and bean serialization and deserialization across TOON implementations.
 */
abstract class AbstractToonBasicSerdeSpec extends Specification implements ToonSpec {

    void "a record with scalar properties round trips"() {
        given:
        def bean = new RecordBean("My Title", 42)

        when:
        def encoded = writeToon(bean)
        def decoded = readToon(encoded, RecordBean)

        then:
        encoded.trim() == "title: My Title\npages: 42"
        decoded == bean
        decoded.title() == "My Title"
        decoded.pages() == 42
    }

    void "a mutable bean round trips"() {
        given:
        def bean = new SimpleBean("Alice", 30)

        when:
        def encoded = writeToon(bean)
        def decoded = readToon(encoded, SimpleBean)

        then:
        decoded.name == "Alice"
        decoded.age == 30
    }

    void "a nested object bean round trips"() {
        given:
        def bean = new ObjectBean("Outer", new SimpleBean("Inner", 25))

        when:
        def encoded = writeToon(bean)
        def decoded = readToon(encoded, ObjectBean)

        then:
        decoded.name() == "Outer"
        decoded.inner().name == "Inner"
        decoded.inner().age == 25
    }

    void "a bean with nullable properties round trips with null values"() {
        given:
        def bean = new NullableBean("Required", null)

        when:
        def encoded = writeToon(bean)
        def decoded = readToon(encoded, NullableBean)

        then:
        decoded.required() == "Required"
        decoded.optional() == null
    }

    void "reading from byte array and input stream produces equal results"() {
        given:
        def bean = new RecordBean("Stream Test", 100)
        def toon = writeToon(bean)
        byte[] bytes = toon.getBytes(StandardCharsets.UTF_8)
        def stream = new ByteArrayInputStream(bytes)

        when:
        def fromString = readToon(toon, RecordBean)
        def fromBytes = readToon(bytes, RecordBean)
        def fromStream = readToon(stream, RecordBean)

        then:
        fromString == bean
        fromBytes == bean
        fromStream == bean
    }

    void "writing to byte array and output stream produces valid TOON"() {
        given:
        def bean = new RecordBean("Output Test", 99)
        def outStream = new ByteArrayOutputStream()

        when:
        def str = writeToon(bean)
        byte[] bytes = writeToonAsBytes(bean)
        writeToon(outStream, Argument.of(RecordBean), bean)

        then:
        new String(bytes, StandardCharsets.UTF_8).trim() == str.trim()
        new String(outStream.toByteArray(), StandardCharsets.UTF_8).trim() == str.trim()
        readToon(bytes, RecordBean) == bean
    }

    void "deserializing with missing optional field succeeds"() {
        given:
        def toon = "required: Present\n"

        when:
        def decoded = readToon(toon, NullableBean)

        then:
        decoded.required() == "Present"
        decoded.optional() == null
    }

    void "deserializing with extra unknown fields ignores the extra fields"() {
        given:
        def toon = "title: Book\npages: 150\nextraField: Ignored\n"

        when:
        def decoded = readToon(toon, RecordBean)

        then:
        decoded.title() == "Book"
        decoded.pages() == 150
    }
}
