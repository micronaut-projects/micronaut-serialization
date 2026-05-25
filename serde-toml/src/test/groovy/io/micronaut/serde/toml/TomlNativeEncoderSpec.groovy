package io.micronaut.serde.toml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.toml.fixture.Book
import io.micronaut.serde.toml.fixture.BookList
import io.micronaut.serde.toml.fixture.ComplexField
import io.micronaut.serde.toml.fixture.FiveMinuteUser
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class TomlNativeEncoderSpec extends Specification {

    @Inject
    @Named("toml")
    ObjectMapper tomlMapper

    void "table layout writes nested objects with table headers"() {
        given:
        // Map.of
        def value = [
            name    : "demo",
            database: [
                server: "192.168.1.1",
                ports : [8000, 8001, 8002]
            ]
        ]
        String expectedTableToml =
"""name = 'demo'

[database]
server = '192.168.1.1'
ports = [8000, 8001, 8002]
"""

        expect:
        tomlMapper.writeValueAsString(value) == expectedTableToml
        tomlMapper.readValue(expectedTableToml, Argument.of(Map)) == value
    }

    void "table layout writes arrays of objects with block array headers"() {
        given:
        def value = [
            name    : "demo",
            products: [
                [name: "Hammer", sku: 738592],
                [name: "Nail", sku: 284758]
            ]
        ]
        String expectedTableToml =
"""name = 'demo'

[[products]]
name = 'Hammer'
sku = 738592

[[products]]
name = 'Nail'
sku = 284758
"""

        expect:
        tomlMapper.writeValueAsString(value) == expectedTableToml
        tomlMapper.readValue(expectedTableToml, Argument.of(Map)) == value
    }

    void "table layout keeps literal dotted keys quoted"() {
        given:
        def value = [
            "quoted.key"     : "literal",
            "database.server": [enabled: true]
        ]
        String expectedTableToml =
"""'quoted.key' = 'literal'

['database.server']
enabled = true
"""

        expect:
        tomlMapper.writeValueAsString(value) == expectedTableToml
        tomlMapper.readValue(expectedTableToml, Argument.of(Map)) == value
    }

    void "table layout writer paths produce identical output"() {
        given:
        def value = [
            name    : "demo",
            products: [
                [name: "Hammer", sku: 738592]
            ]
        ]
        String expectedTableToml =
"""name = 'demo'

[[products]]
name = 'Hammer'
sku = 738592
"""
        def output = new ByteArrayOutputStream()

        when:
        String stringToml = tomlMapper.writeValueAsString(value)
        byte[] byteToml = tomlMapper.writeValueAsBytes(value)
        tomlMapper.writeValue(output, value)

        then:
        stringToml == expectedTableToml
        byteToml == expectedTableToml.getBytes(StandardCharsets.UTF_8)
        output.toByteArray() == byteToml
        tomlMapper.readValue(stringToml, Argument.of(Map)) == value
    }

    void "inline layout writes nested objects as inline tables"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.write-layout': 'inline'
        ])
        def mapper = ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))
        def value = [
            name    : "demo",
            database: [
                server: "192.168.1.1",
                ports : [8000, 8001, 8002]
            ]
        ]
        String expectedInlineToml =
"""name = 'demo'
database = {server = '192.168.1.1', ports = [8000, 8001, 8002]}
"""

        expect:
        mapper.writeValueAsString(value) == expectedInlineToml
        mapper.readValue(expectedInlineToml, Argument.of(Map)) == value

        cleanup:
        ctx.close()
    }

    void "inline layout writes arrays of objects as arrays of inline tables"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.write-layout': 'inline'
        ])
        def mapper = ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))
        def value = [
            name    : "demo",
            products: [
                [name: "Hammer", sku: 738592],
                [name: "Nail", sku: 284758]
            ]
        ]
        String expectedInlineToml =
"""name = 'demo'
products = [{name = 'Hammer', sku = 738592}, {name = 'Nail', sku = 284758}]
"""

        expect:
        mapper.writeValueAsString(value) == expectedInlineToml
        mapper.readValue(expectedInlineToml, Argument.of(Map)) == value

        cleanup:
        ctx.close()
    }

    void "inline layout writer paths produce identical output"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.write-layout': 'inline'
        ])
        def mapper = ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))
        def value = [
            name    : "demo",
            products: [
                [name: "Hammer", sku: 738592]
            ]
        ]
        String expectedInlineToml =
"""name = 'demo'
products = [{name = 'Hammer', sku = 738592}]
"""
        def output = new ByteArrayOutputStream()

        when:
        String stringToml = mapper.writeValueAsString(value)
        byte[] byteToml = mapper.writeValueAsBytes(value)
        mapper.writeValue(output, value)

        then:
        stringToml == expectedInlineToml
        byteToml == expectedInlineToml.getBytes(StandardCharsets.UTF_8)
        output.toByteArray() == byteToml
        mapper.readValue(stringToml, Argument.of(Map)) == value

        cleanup:
        ctx.close()
    }

    void "inline layout fails cleanly for non document root values"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.write-layout': 'inline'
        ])
        def mapper = ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))

        when:
        mapper.writeValueAsString([1, 2, 3])

        then:
        Exception e = thrown()
        hasMessageFragment(e, "root value must be an object")

        cleanup:
        ctx.close()
    }

    void "string bytes and stream writer paths produce equivalent readable TOML"() {
        given:
        def value = [
            name  : "demo",
            ports : [8080, 8081],
            server: [
                enabled     : true,
                "quoted.key": "literal"
            ]
        ]
        def output = new ByteArrayOutputStream()

        when:
        String stringToml = tomlMapper.writeValueAsString(value)
        byte[] byteToml = tomlMapper.writeValueAsBytes(value)
        tomlMapper.writeValue(output, value)
        def fromString = tomlMapper.readValue(stringToml, Argument.of(Map))
        def fromBytes = tomlMapper.readValue(byteToml, Argument.of(Map))
        def fromStream = tomlMapper.readValue(output.toByteArray(), Argument.of(Map))

        then:
        byteToml == stringToml.getBytes(StandardCharsets.UTF_8)
        output.toByteArray() == byteToml
        [fromString, fromBytes, fromStream].each {
            assert it.name == "demo"
            assert it.ports == [8080, 8081]
            assert it.server.enabled == true
            assert it.server["quoted.key"] == "literal"
        }
    }

    void "native encoder round trips representative beans"() {
        given:
        def user = new FiveMinuteUser("Bob", "Palmer", FiveMinuteUser.Gender.MALE, true, [1, 2, 3, 4] as byte[])
        def book = new Book("Micronaut in Action", 320, new Book.Author("Ada"))
        def list = new BookList()
        list.books = [book, new Book("TOML in Action", 280, new Book.Author("Bob"))]

        expect:
        tomlMapper.readValue(tomlMapper.writeValueAsString(user), Argument.of(FiveMinuteUser)) == user

        and:
        def fromBook = tomlMapper.readValue(tomlMapper.writeValueAsString(book), Argument.of(Book))
        fromBook.title == "Micronaut in Action"
        fromBook.pages == 320
        fromBook.author.name == "Ada"

        and:
        def fromList = tomlMapper.readValue(tomlMapper.writeValueAsString(list), Argument.of(BookList))
        fromList.books*.title == ["Micronaut in Action", "TOML in Action"]
        fromList.books*.author*.name == ["Ada", "Bob"]
    }

    void "native encoder preserves deterministic scalar structural and key output"() {
        expect:
        tomlMapper.writeValueAsString(value) == expected
        tomlMapper.readValue(expected, Argument.of(Map)) == tomlMapper.readValue(tomlMapper.writeValueAsString(value), Argument.of(Map))

        where:
        value                        | expected
        [abc: 123]                   | "abc = 123\n"
        [abc: true]                  | "abc = true\n"
        [abc: 1.23d]                 | "abc = 1.23\n"
        [abc: [:]]                   | "[abc]\n"
        [abc: []]                    | "abc = []\n"
        [abc: [1, [foo: 1, bar: 2]]] | "abc = [1, {foo = 1, bar = 2}]\n"
        ["foo bar": 123]             | "'foo bar' = 123\n"
        ["quoted.key": "literal"]    | "'quoted.key' = 'literal'\n"
    }

    void "native encoder preserves string escaping contract"() {
        expect:
        tomlMapper.writeValueAsString([abc: input]) == expected
        tomlMapper.readValue(expected, Argument.of(Map)).abc == input

        where:
        input       | expected
        "foo"       | "abc = 'foo'\n"
        "foo'"      | "abc = \"foo'\"\n"
        'foo"'      | "abc = 'foo\"'\n"
        'foo"\''    | "abc = \"foo\\\"'\"\n"
        "foo\u0001" | "abc = \"foo\\u0001\"\n"
        "foo\b"     | "abc = \"foo\\b\"\n"
        "line\nnext" | "abc = \"line\\nnext\"\n"
    }

    void "native encoder preserves binary base64 text and null sentinel writes"() {
        given:
        def user = new FiveMinuteUser("Bob", "Palmer", FiveMinuteUser.Gender.MALE, true, [1, 2, 3, 4] as byte[])
        def bean = new ComplexField()

        expect:
        tomlMapper.writeValueAsString(user).contains("userImage = 'AQIDBA=='\n")
        tomlMapper.writeValueAsString(bean) == "foo = ''\n"
        tomlMapper.readValue(tomlMapper.writeValueAsString(bean), Argument.of(ComplexField)).foo == null
    }

    void "native encoder honors fail on null write"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.write-features.fail-on-null-write': true
        ])
        def mapper = ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))

        when:
        mapper.writeValueAsString(new ComplexField())

        then:
        Exception e = thrown()
        hasMessageFragment(e, "null writing disabled", "FAIL_ON_NULL_WRITE")

        cleanup:
        ctx.close()
    }

    private static boolean hasMessageFragment(Throwable throwable, String... fragments) {
        Throwable current = throwable
        while (current != null) {
            if (current.message != null && fragments.any { current.message.contains(it) }) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
