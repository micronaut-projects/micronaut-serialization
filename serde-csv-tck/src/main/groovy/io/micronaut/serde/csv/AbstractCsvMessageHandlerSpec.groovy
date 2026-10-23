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
package io.micronaut.serde.csv

import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.serde.csv.body.CsvMessageHandler
import io.micronaut.serde.csv.mapper.CsvMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@MicronautTest
abstract class AbstractCsvMessageHandlerSpec extends Specification {

    @Inject
    CsvMapper csvMapper

    private CsvMessageHandler<Object> createCodec() {
        new CsvMessageHandler<>(csvMapper)
    }

    void "read with iterable type"() {
        when:
        def codec = createCodec()
        def input = new ByteArrayInputStream("name,age\nAlice,30".getBytes())
        def type = (Argument<Object>) (Argument<?>) Argument.of(List, Map)
        def result = codec.read(type, new MediaType("text/csv"), null, input) as List<Map<String, String>>

        then:
        result.size() == 1
        result[0]["name"] == "Alice"
        result[0]["age"] == "30"
    }

    void "write to output stream"() {
        when:
        def codec = createCodec()
        def output = new ByteArrayOutputStream()
        def type = Argument.of(ArrayList, String)
        def data = [
            [name: "Alice", age: "30"],
            [name: "Bob", age: "25"],
        ]
        codec.writeTo(type, new MediaType("text/csv"), data, null, output)
        def result = output.toString(StandardCharsets.UTF_8)

        then:
        result == "Alice,30\nBob,25\n"
    }

    void "write to with null output stream"() {
        when:
        def codec = createCodec()
        def type = (Argument<Object>) (Argument<?>) Argument.of(String)
        codec.writeTo(type, new MediaType("text/csv"), "single", null, null as OutputStream)

        then:
        noExceptionThrown()
    }

    void "write to with null type"() {
        when:
        def codec = createCodec()
        def output = new ByteArrayOutputStream()
        codec.writeTo(null, new MediaType("text/csv"), [name: "Alice", age: "30"], null, output)
        def result = output.toString(Charset.forName("UTF-8"))

        then:
        result == "Alice,30\n"
    }
}
