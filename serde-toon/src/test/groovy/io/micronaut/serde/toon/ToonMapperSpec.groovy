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
package io.micronaut.serde.toon

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.config.SerdeConfiguration
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.toon.data.Book
import io.micronaut.serde.toon.data.MutableBook
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Covers the {@link ToonMapper} behaviors {@link ToonMapperSmokeSpec} doesn't:
 * {@code updateValue}/{@code updateValueFromTree}, and that
 * {@code cloneWithViewClass}/{@code cloneWithConfiguration} return a working,
 * independently-configured mapper rather than silently returning {@code this}.
 */
@MicronautTest
class ToonMapperSpec extends Specification {

    @Inject
    ToonMapper mapper

    void 'test updateValue merges toon into an existing value'() {
        given:
        def book = new MutableBook(title: 'Draft', pages: 1)

        when:
        mapper.updateValue(book, Argument.of(MutableBook), 'pages: 300'.bytes)

        then:
        book.title == 'Draft'
        book.pages == 300

        when:
        mapper.updateValue(book, Argument.of(MutableBook), new ByteArrayInputStream('title: Final'.bytes))

        then:
        book.title == 'Final'
        book.pages == 300

        when:
        mapper.updateValueFromTree(book, JsonNode.createObjectNode([pages: JsonNode.createNumberNode(5)]))

        then:
        book.pages == 5

        when: 'a null document leaves the value alone'
        mapper.updateValue(book, Argument.of(MutableBook), 'null'.bytes)

        then:
        book.title == 'Final'
    }

    void 'test cloneWithViewClass returns a distinct, working mapper'() {
        when:
        def viewed = mapper.cloneWithViewClass(Object)

        then:
        viewed instanceof ToonMapper
        !viewed.is(mapper)
        viewed.writeValueAsBytes(new Book('A', 1)) == 'title: A\npages: 1'.bytes
    }

    void 'test cloneWithConfiguration returns a distinct mapper honoring its own configuration'() {
        given:
        def stricter = ApplicationContext.run(['micronaut.serde.maximum-nesting-depth': '2'])
        def stricterConfiguration = stricter.getBean(SerdeConfiguration)
        stricter.close()

        when:
        def reconfigured = mapper.cloneWithConfiguration(stricterConfiguration, null, null)

        then:
        reconfigured instanceof ToonMapper
        !reconfigured.is(mapper)

        when: 'the clone enforces the stricter nesting depth it was given'
        reconfigured.readValue('a:\n  b:\n    c: 1'.getBytes('UTF-8'), Argument.of(Object))

        then:
        thrown(SerdeException)

        when: 'the original mapper is unaffected, still using its own configuration'
        def result = mapper.readValue('a:\n  b:\n    c: 1'.getBytes('UTF-8'), Argument.of(Object))

        then:
        result != null
    }

    void 'test the mapper picks up the coercion policy from configuration'() {
        given:
        def context = ApplicationContext.run(['micronaut.serde.deserialization.coercion-mode': 'STRICT'])
        def strictMapper = context.getBean(ToonMapper)

        when: 'a quoted (string-shaped) number is not the natural shape of an Integer field'
        strictMapper.readValue('number: "42"'.getBytes('UTF-8'), Argument.of(Plain))

        then:
        thrown(SerdeException)

        when: 'a well-shaped value still reads under the strict policy'
        def wellShaped = strictMapper.readValue('number: 42'.getBytes('UTF-8'), Argument.of(Plain))

        then:
        wellShaped.number == 42

        when: 'the default mapper is unaffected, still lenient'
        def lenient = mapper.readValue('number: "42"'.getBytes('UTF-8'), Argument.of(Plain))

        then:
        lenient.number == 42

        cleanup:
        context.close()
    }

    @Serdeable
    static class Plain {
        Integer number
    }
}
