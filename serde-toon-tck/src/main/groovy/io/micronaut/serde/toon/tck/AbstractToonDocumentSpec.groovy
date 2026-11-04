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

/**
 * Validates document-level structure, root types, and comments.
 */
abstract class AbstractToonDocumentSpec extends Specification implements ToonSpec {

    void "a root object is read"() {
        expect:
        readToon("title: Book\npages: 300\n", RecordBean) == new RecordBean("Book", 300)
    }

    void "a root scalar is read"() {
        expect:
        readToon("hello\n", String) == "hello"
        readToon("42\n", Integer) == 42
        readToon("true\n", Boolean)
    }

    void "comments are ignored"() {
        expect:
        readToon('''# leading comment
title: Book
# middle comment
pages: 300
# trailing comment
''', RecordBean) == new RecordBean("Book", 300)
    }

    void "empty input decodes to an empty object"() {
        expect:
        readToon(toon, Argument.mapOf(String, Object)).isEmpty()

        where:
        toon << ["", "   ", "# only a comment\n"]
    }

    void "malformed syntax is rejected"() {
        when:
        readToon(toon, RecordBean)

        then:
        thrown(Exception)

        where:
        toon << [
                "title: \"unclosed\npages: 1\n",
                "title: Book\ntitle: Duplicate\npages: 1\n"
        ]
    }

    void "a document written by the mapper is read back accurately"() {
        given:
        def bean = new ObjectBean("Outer", new SimpleBean("Alice", 30))

        expect:
        readToon(writeToon(bean), ObjectBean).inner().name == "Alice"
    }
}
