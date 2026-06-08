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
package io.micronaut.serde.properties

import io.micronaut.core.type.Argument

import java.nio.charset.StandardCharsets

abstract class AbstractPropSerializationSpec extends AbstractPropDeserializationSpec {

    void 'test serialize a flat bean to properties bytes and stream'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Server {
    private String host;
    private int port;
    private boolean secure;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
}
''')
        def type = argumentOf(context, 'test.Server')
        def server = newInstance(context, 'test.Server', [
                host  : 'localhost',
                port  : 8080,
                secure: true
        ])
        def expectedLines = ['host=localhost', 'port=8080', 'secure=true']

        when:
        def bytesOutput = writeProperties(server)
        def outputStream = new ByteArrayOutputStream()
        writeProperties(outputStream, type, server)
        def streamOutput = new String(outputStream.toByteArray(), StandardCharsets.UTF_8)

        then:
        bytesOutput.readLines().sort() == expectedLines.sort()
        streamOutput.readLines().sort() == expectedLines.sort()

        when:
        def roundTripped = readProperties(bytesOutput, type)

        then:
        roundTripped.host == 'localhost'
        roundTripped.port == 8080
        roundTripped.secure == true

        cleanup:
        context.close()
    }

    void 'test serialize nested objects lists and maps to flattened properties'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Map;

@Serdeable
class Library {
    private Book book;
    public Book getBook() { return book; }
    public void setBook(Book book) { this.book = book; }
}

@Serdeable
class Book {
    private String title;
    private List<Author> authors;
    private Map<String, Author> authorsByInitials;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<Author> getAuthors() { return authors; }
    public void setAuthors(List<Author> authors) { this.authors = authors; }
    public Map<String, Author> getAuthorsByInitials() { return authorsByInitials; }
    public void setAuthorsByInitials(Map<String, Author> authorsByInitials) { this.authorsByInitials = authorsByInitials; }
}

@Serdeable
class Author {
    private String name;
    private int age;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
''')
        def type = argumentOf(context, 'test.Library')
        def king = newInstance(context, 'test.Author', [name: 'Stephen King', age: 60])
        def tolkien = newInstance(context, 'test.Author', [name: 'JRR Tolkien', age: 81])
        def book = newInstance(context, 'test.Book', [
                title            : 'The Stand',
                authors          : [king, tolkien],
                authorsByInitials: [SK: king]
        ])
        def library = newInstance(context, 'test.Library', [book: book])

        when:
        def properties = writeProperties(type, library)
        def lines = properties.readLines()

        then:
        lines.contains('book.title=The Stand')
        lines.contains('book.authors.1.age=60')
        lines.contains('book.authors.1.name=Stephen King')
        lines.contains('book.authors.2.age=81')
        lines.contains('book.authors.2.name=JRR Tolkien')
        lines.contains('book.authorsByInitials.SK.age=60')
        lines.contains('book.authorsByInitials.SK.name=Stephen King')

        when:
        def roundTripped = readProperties(properties, type)

        then:
        roundTripped.book.title == 'The Stand'
        roundTripped.book.authors.size() == 2
        roundTripped.book.authors[0].name == 'Stephen King'
        roundTripped.book.authors[0].age == 60
        roundTripped.book.authors[1].name == 'JRR Tolkien'
        roundTripped.book.authors[1].age == 81
        roundTripped.book.authorsByInitials['SK'].name == 'Stephen King'
        roundTripped.book.authorsByInitials['SK'].age == 60

        cleanup:
        context.close()
    }

    void 'test serialize properties escapes special key and value characters'() {
        given:
        buildContext('package test; class Placeholder {}')
        def value = 'value=:#!\\' + '\n' + 'Omega \u03a9'
        def values = ['key value': value]
        def type = Argument.mapOf(String, String)

        when:
        def properties = writeProperties(type, values)
        def parsed = readProperties(properties, type)

        then:
        parsed['key value'] == value
    }
}
