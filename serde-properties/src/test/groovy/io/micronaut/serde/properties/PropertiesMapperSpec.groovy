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
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.Ignore

import java.nio.charset.StandardCharsets

class PropertiesMapperSpec extends PropertiesCompileSpec {

    void 'test readValueFromTree decodes an already typed tree'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Server {
    private String host;
    private int port;
    private boolean secure;
    private double ratio;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public double getRatio() { return ratio; }
    public void setRatio(double ratio) { this.ratio = ratio; }
}
''')
        def type = argumentOf(context, 'test.Server')
        def tree = JsonNode.createObjectNode([
                host  : JsonNode.createStringNode('localhost'),
                port  : JsonNode.createNumberNode(8080),
                secure: JsonNode.createBooleanNode(true),
                ratio : JsonNode.createNumberNode(1.5d)
        ])

        when:
        def server = jsonMapper.readValueFromTree(tree, type)

        then:
        server.host == 'localhost'
        server.port == 8080
        server.secure
        server.ratio == 1.5d

        cleanup:
        context.close()
    }

    void 'test indexed scalar list respects configured array size threshold'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Tags {
    private List<String> values;
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
''', ['micronaut.serde.deserialization.array-size-threshold': 2])
        def type = argumentOf(context, 'test.Tags')

        when:
        def tags = readProperties('''
values[1]=b
values[0]=a
''', type)

        then:
        tags.values == ['a', 'b']

        when:
        readProperties('''
values[2]=c
''', type)

        then:
        def e = thrown SerdeException
        e.message == 'Array index [2] exceeds the configured array size threshold [2]'

        cleanup:
        context.close()
    }

    void 'test default array index style uses bracketed zero based indexes'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Tags {
    private List<String> values;
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
''')
        def type = argumentOf(context, 'test.Tags')

        when:
        def tags = readProperties('''
values[1]=b
values[0]=a
''', type)

        then:
        tags.values == ['a', 'b']

        when:
        def properties = writeProperties(newInstance(context, 'test.Tags', [values: ['a', 'b']]))

        then:
        properties.readLines().sort() == ['values[0]=a', 'values[1]=b'].sort()

        cleanup:
        context.close()
    }

    void 'test configured bracketed array index style reads and writes zero based indexes'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Tags {
    private List<String> values;
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
''', ['micronaut.serde.format.properties.array-index-style': 'BRACKETED'])
        def type = argumentOf(context, 'test.Tags')

        when:
        def tags = readProperties('''
values[1]=b
values[0]=a
''', type)

        then:
        tags.values == ['a', 'b']

        when:
        def properties = writeProperties(newInstance(context, 'test.Tags', [values: ['a', 'b']]))

        then:
        properties.readLines().sort() == ['values[0]=a', 'values[1]=b'].sort()

        cleanup:
        context.close()
    }

    void 'test bracketed array index style'() {
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
''', ['micronaut.serde.format.properties.array-index-style': 'BRACKETED'])
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
        def properties = writeProperties(library)
        def lines = properties.readLines()

        then:
        lines.contains('book.title=The Stand')
        lines.contains('book.authors[0].age=60')
        lines.contains('book.authors[0].name=Stephen King')
        lines.contains('book.authors[1].age=81')
        lines.contains('book.authors[1].name=JRR Tolkien')
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

    void 'test Dotted array index style'() {
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
''', ['micronaut.serde.format.properties.array-index-style': 'DOTTED'])

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
        def properties = writeProperties(library)
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

    void 'test dotted array index style reads and writes one based dotted indexes'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
class Tags {
    private List<String> values;
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
''', ['micronaut.serde.format.properties.array-index-style': 'DOTTED'])
        def type = argumentOf(context, 'test.Tags')

        when:
        def tags = readProperties('''
values.2=b
values.1=a
''', type)

        then:
        tags.values == ['a', 'b']

        when:
        def properties = writeProperties(newInstance(context, 'test.Tags', [values: ['a', 'b']]))

        then:
        properties.readLines().sort() == ['values.1=a', 'values.2=b'].sort()

        cleanup:
        context.close()
    }

    void 'test terminal map index is deserialized as map entry'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class Authors {
    private Map<String, String> authorsByInitials;
    public Map<String, String> getAuthorsByInitials() { return authorsByInitials; }
    public void setAuthorsByInitials(Map<String, String> authorsByInitials) { this.authorsByInitials = authorsByInitials; }
}
''')
        def type = argumentOf(context, 'test.Authors')

        when:
        def authors = readProperties('authorsByInitials[SK]=Stephen King\n', type)

        then:
        authors.authorsByInitials == [SK: 'Stephen King']

        cleanup:
        context.close()
    }

    void 'test leading value space is escaped when serializing properties'() {
        given:
        def context = buildContext('package test; class Placeholder {}')
        def type = Argument.mapOf(String, String)
        def value = ' leading=:#!\\' + '\n' + 'Omega \u03a9'

        when:
        def properties = new String(jsonMapper.writeValueAsBytes(type, ['key value': value]), StandardCharsets.UTF_8)
        def parsed = jsonMapper.readValue(properties.getBytes(StandardCharsets.UTF_8), type)

        then:
        parsed['key value'] == value

        cleanup:
        context.close()
    }

    void 'test serialize rejects root scalars because properties require keys'() {
        given:
        def context = buildContext('package test; class Placeholder {}')

        when:
        jsonMapper.writeValueAsBytes(Argument.of(JsonNode), JsonNode.createStringNode('value'))

        then:
        thrown(IOException)

        cleanup:
        context.close()
    }
}
