package io.micronaut.serde.toml

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.toml.fixture.Book
import io.micronaut.serde.toml.fixture.BookList
import io.micronaut.serde.toml.fixture.ComplexField
import io.micronaut.serde.toml.fixture.FiveMinuteUser
import io.micronaut.serde.toml.fixture.Point
import io.micronaut.serde.toml.fixture.PointWrapper
import jakarta.inject.Inject
import jakarta.inject.Named
import io.micronaut.test.extensions.spock.annotation.MicronautTest

import java.nio.charset.StandardCharsets

@MicronautTest
class TomlObjectMapperSpec extends AbstractMicronautTomlSerdeSpec {

    @Inject
    @Named("toml")
    TomlObjectMapper tomlMapper

    void "writeValueToTree serializes a pojo completely"() {
        given:
        def author = new Book.Author("Ada")
        def book = new Book("Micronaut in Action", 320, author)

        when:
        JsonNode tree = tomlMapper.writeValueToTree(book)

        then:
        tree.get("title").stringValue == "Micronaut in Action"
        tree.get("pages").intValue == 320
        tree.get("author").get("name").stringValue == "Ada"
    }

    void "readValueFromTree deserializes a pojo completely"() {
        given:
        JsonNode tree = JsonNode.createObjectNode([
                title : JsonNode.createStringNode("Micronaut in Action"),
                pages : JsonNode.createNumberNode(320),
                author: JsonNode.createObjectNode([
                        name: JsonNode.createStringNode("Ada")
                ])
        ])

        when:
        Book book = tomlMapper.readValueFromTree(tree, Argument.of(Book))

        then:
        book.title == "Micronaut in Action"
        book.pages == 320
        book.author != null
        book.author.name == "Ada"
    }

    void "tree conversion stays consistent with toml text conversion"() {
        given:
        def first = new Book("Micronaut in Action", 320, new Book.Author("Ada"))
        def second = new Book("TOML in Action", 280, new Book.Author("Bob"))
        def books = new BookList()
        books.books = [first, second]

        when:
        JsonNode tree = tomlMapper.writeValueToTree(books)
        BookList fromTree = tomlMapper.readValueFromTree(tree, Argument.of(BookList))
        BookList fromToml = tomlMapper.readValue(tomlMapper.writeValueAsString(books), Argument.of(BookList))

        then:
        fromTree.books*.title == fromToml.books*.title
        fromTree.books*.pages == fromToml.books*.pages
        fromTree.books*.author*.name == fromToml.books*.author*.name
    }

    void "writeValueToTree uses base64 strings for byte arrays like the toml text path"() {
        given:
        def user = new FiveMinuteUser("Bob", "Palmer",
                FiveMinuteUser.Gender.MALE, true, [1, 2, 3, 4] as byte[])

        when:
        JsonNode tree = tomlMapper.writeValueToTree(user)
        FiveMinuteUser fromTree = tomlMapper.readValueFromTree(tree, Argument.of(FiveMinuteUser))

        then:
        tree.get("userImage").stringValue == "AQIDBA=="
        fromTree == user
    }

    void "null reference fields round trip consistently across string bytes and tree paths"() {
        given:
        def bean = new ComplexField()
        def expected = "foo = ''\n"

        when:
        def stringResult = tomlMapper.writeValueAsString(bean)
        def bytesResult = tomlMapper.writeValueAsBytes(bean)
        JsonNode tree = tomlMapper.writeValueToTree(bean)
        ComplexField fromString = tomlMapper.readValue(stringResult, Argument.of(ComplexField))
        ComplexField fromBytes = tomlMapper.readValue(bytesResult, Argument.of(ComplexField))
        ComplexField fromTree = tomlMapper.readValueFromTree(tree, Argument.of(ComplexField))

        then:
        stringResult == expected
        bytesResult == expected.bytes
        tree.get("foo").isNull()
        fromString.foo == null
        fromBytes.foo == null
        fromTree.foo == null
    }

    void "writeValue OutputStream preserves nested dotted output exactly like writeValueAsString"() {
        given:
        def wrapper = new PointWrapper(new Point(19, 72))
        def expected = "[point]\nx = 19\ny = 72\n"
        def output = new ByteArrayOutputStream()

        when:
        tomlMapper.writeValue(output, wrapper)
        def outputText = output.toString(StandardCharsets.UTF_8)

        then:
        output.toByteArray() == expected.bytes
        outputText == expected
        tomlMapper.writeValueAsString(wrapper) == expected
    }

    void "public mapper writes jackson compatible scalar and structural toml deterministically"() {
        expect:
        tomlMapper.writeValueAsString(value) == expected
        tomlMapper.writeValueAsBytes(value) == expected.getBytes(StandardCharsets.UTF_8)

        where:
        value                        | expected
        [abc: 123]                   | "abc = 123\n"
        [abc: true]                  | "abc = true\n"
        [abc: 1.23d]                 | "abc = 1.23\n"
        [abc: [:]]                   | "[abc]\n"
        [abc: []]                    | "abc = []\n"
        [abc: [1, [foo: 1, bar: 2]]] | "abc = [1, {foo = 1, bar = 2}]\n"
        ["foo bar": 123]             | "'foo bar' = 123\n"
    }

    void "public mapper writes jackson compatible quoted string variants deterministically"() {
        expect:
        tomlMapper.writeValueAsString([abc: input]) == expected
        tomlMapper.writeValueAsBytes([abc: input]) == expected.getBytes(StandardCharsets.UTF_8)

        where:
        input    | expected
        "foo"    | "abc = 'foo'\n"
        "foo'"   | "abc = \"foo'\"\n"
        'foo"'   | "abc = 'foo\"'\n"
        'foo"\'' | "abc = \"foo\\\"'\"\n"
        "foo\u0001" | "abc = \"foo\\u0001\"\n"
        "foo\b"  | "abc = \"foo\\b\"\n"
    }

    void "public mapper writes jackson compatible base64 text for binary values"() {
        given:
        def user = new FiveMinuteUser("Bob", "Palmer", FiveMinuteUser.Gender.MALE, true, [1, 2, 3, 4] as byte[])
        def expected = """firstName = 'Bob'
lastName = 'Palmer'
gender = 'MALE'
verified = true
userImage = 'AQIDBA=='
"""

        expect:
        tomlMapper.writeValueAsString(user) == expected
        tomlMapper.writeValueAsBytes(user) == expected.getBytes(StandardCharsets.UTF_8)
    }

    void "FiveMinuteUser toml trees stay aligned with json trees field-for-field"() {
        given:
        def user = new FiveMinuteUser("Bob", "Palmer", FiveMinuteUser.Gender.MALE, true, [1, 2, 3, 4] as byte[])
        def expectedImage = Base64.encoder.encodeToString(user.userImage)   // java.util.Base64

        when:
        JsonNode tomlTree = tomlMapper.writeValueToTree(user)
        def tomlTextTree  = tomlMapper.readValueFromTree(tomlTree, Argument.of(Map))
        JsonNode expectedTree = JsonNode.createObjectNode([
                firstName: JsonNode.createStringNode(user.firstName),
                lastName : JsonNode.createStringNode(user.lastName),
                gender   : JsonNode.createStringNode(user.gender.name()),
                verified : JsonNode.createBooleanNode(user.verified),
                userImage: JsonNode.createStringNode(expectedImage)
        ])

        then:
        tomlTree == expectedTree
        tomlTextTree == [firstName: user.firstName, lastName: user.lastName,
                         gender: user.gender.name(), verified: user.verified, userImage: expectedImage]
    }

    void "writeValueToTree returns null nodes for null inputs"() {
        expect:
        tomlMapper.writeValueToTree(null).isNull()
        tomlMapper.writeValueToTree(Argument.of(Book), null).isNull()
    }

    void "readValueFromTree returns null for null node input"() {
        expect:
        tomlMapper.readValueFromTree(JsonNode.nullNode(), Argument.of(Book)) == null
    }

    void "broken input streams fail cleanly through the public toml mapper"() {
        given:
        def input = new InputStream() {
            @Override
            int read() throws IOException {
                throw new IOException("Test stream failure")
            }
        }

        when:
        tomlMapper.readValue(input, Argument.of(Map))

        then:
        Exception e = thrown()
        e.message.contains("Test stream failure") || e.message.contains("I/O error") || e.cause?.message?.contains("Test stream failure")
    }

    void "cyclic self references fail quickly and safely during string writes"() {
        given:
        List<Object> list = new ArrayList<>()
        list.add(list)

        when:
        tomlMapper.writeValueAsString(list)

        then:
        Exception e = thrown()
        e.message.contains("nesting depth") || e.message.contains("Maximum depth exceeded")
    }

    void "cyclic self references fail quickly and safely during byte writes"() {
        given:
        List<Object> list = new ArrayList<>()
        list.add(list)

        when:
        tomlMapper.writeValueAsBytes(list)

        then:
        Exception e = thrown()
        e.message.contains("nesting depth") || e.message.contains("Maximum depth exceeded")
    }
}
