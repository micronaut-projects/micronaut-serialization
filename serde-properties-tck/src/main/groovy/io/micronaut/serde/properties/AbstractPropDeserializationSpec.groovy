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

abstract class AbstractPropDeserializationSpec extends AbstractPropCompileSpec {

    void 'test scalar coercion to primitive and BigDecimal fields'() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;

@Serdeable
class Bag {
    private char grade;
    private byte b;
    private short s;
    private BigDecimal amount;

    public char getGrade() { return grade; }
    public void setGrade(char grade) { this.grade = grade; }
    public byte getB() { return b; }
    public void setB(byte b) { this.b = b; }
    public short getS() { return s; }
    public void setS(short s) { this.s = s; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
''')
        def type = argumentOf(context, 'test.Bag')

        when:
        def bag = readProperties('''
grade=A
b=12
s=300
amount=9.99
''', type)

        then:
        bag.grade == ('A' as char)
        bag.b == (12 as byte)
        bag.s == (300 as short)
        bag.amount == 9.99G

        cleanup:
        context.close()
    }

    void 'test deserialize a flat .properties document with primitive coercion'() {
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

        when:
        def server = readProperties('''
host=localhost
port=8080
secure=true
''', type)

        then:
        server.host == 'localhost'
        server.port == 8080
        server.secure

        cleanup:
        context.close()
    }

    void 'test deserialize nested objects indexed lists and maps'() {
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

        when:
        def library = readProperties('''
book.title=The Stand
book.authors.1.name=Stephen King
book.authors.1.age=60
book.authors.2.name=JRR Tolkien
book.authors.2.age=81
book.authorsByInitials.SK.name=Stephen King
book.authorsByInitials.SK.age=60
''', type)
        def book = library.book

        then:
        book.title == 'The Stand'
        book.authors.size() == 2
        book.authors[0].name == 'Stephen King'
        book.authors[0].age == 60
        book.authors[1].name == 'JRR Tolkien'
        book.authors[1].age == 81
        book.authorsByInitials['SK'].name == 'Stephen King'
        book.authorsByInitials['SK'].age == 60

        cleanup:
        context.close()
    }

    void 'test deserialize a list of scalars'() {
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
values.2=b
values.1=a
values.3=c
''', type)

        then:
        tags.values == ['a', 'b', 'c']

        cleanup:
        context.close()
    }

    void 'test deserialize directly into a Map'() {
        given:
        buildContext('package test; class Placeholder {}')

        when:
        def map = readProperties('a.b=1\na.c=hello\n', Argument.mapOf(String, Object))

        then:
        map.a.b == '1'
        map.a.c == 'hello'
    }
}
