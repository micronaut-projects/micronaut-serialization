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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.serde.csv.body.CsvMessageHandler
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@MicronautTest
abstract class AbstractCsvMessageHandlerSpec extends Specification {

    @Inject
    CsvObjectMapper csvObjectMapper

    @Inject
    @Client("/")
    HttpClient client

    @Controller("/csv")
    static class CsvController {

        @Post(uri = "/echo", consumes = MediaType.TEXT_CSV, produces = MediaType.TEXT_CSV)
        Person echo(@Body Person person) {
            return person
        }
    }

    private CsvMessageHandler<Object> createCodec() {
        new CsvMessageHandler<>(csvObjectMapper)
    }

    void "read with iterable type"() {
        when:
        def codec = createCodec()
        def input = new ByteArrayInputStream("name,age\nAlice,30".getBytes())
        def type = (Argument<Object>) (Argument<?>) Argument.of(List, Map)
        def result = codec.read(type, new MediaType("text/csv"), null, input)

        then:
        result instanceof List
        result.size() == 1
        result[0]["name"] == "Alice"
        result[0]["age"] == "30"
    }

    void "read with set type"() {
        when:
        def codec = createCodec()
        def input = new ByteArrayInputStream("name,age\nAlice,30".getBytes())
        def type = (Argument<Object>) (Argument<?>) Argument.of(Set, Map)
        def result = codec.read(type, new MediaType("text/csv"), null, input)
        def entry = result.iterator().next()

        then:
        result instanceof LinkedHashSet
        result.size() == 1
        entry["name"] == "Alice"
        entry["age"] == "30"
    }

    void "unsupported collection types are not readable"() {
        when:
        def codec = createCodec()
        def list = (Argument<Object>) (Argument<?>) Argument.of(List, String)
        def sortedSet = (Argument<Object>) (Argument<?>) Argument.of(SortedSet, String)

        then:
        codec.isReadable(list, new MediaType("text/csv"))
        !codec.isReadable(sortedSet, new MediaType("text/csv"))
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

    def "post and echo honors content-type charset"() {
        given:
        // Header row names match the Person bean properties. Build the request bytes as
        // ISO-8859-1 so 'é' is a single byte 0xE9 (two bytes 0xC3 0xA9 in UTF-8).
        def csv = "name,age\nZoé,42\n"
        def requestBytes = csv.getBytes(StandardCharsets.ISO_8859_1)

        when:
        def exchange = client.toBlocking().exchange(
                HttpRequest.POST("/csv/echo", requestBytes)
                        .contentType(new MediaType("text/csv", [charset: "ISO-8859-1"]))
                        .accept(new MediaType("text/csv", [charset: "ISO-8859-1"])),
                byte[]
        )

        then:
        exchange.status() == HttpStatus.OK
        def body = exchange.body()
        body != null
        // The server decoded the request using the charset from the content-type header and
        // encoded the response using the same charset. 'é' is 0xE9 in ISO-8859-1.
        new String(body, StandardCharsets.ISO_8859_1) == "Zoé,42\n"
        body.any { b -> b == (byte) 0xE9 }
    }
}
