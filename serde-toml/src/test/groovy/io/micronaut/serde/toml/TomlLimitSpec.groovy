package io.micronaut.serde.toml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.toml.fixture.StringWrapper
import spock.lang.Specification
import spock.lang.Unroll

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64

class TomlLimitSpec extends Specification {
    private static final int TYPED_STRING_LIMIT = 256
    private static final int TYPED_STRING_LEN = 512

    void "explicit toml public mapper string constraints fail cleanly"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': TYPED_STRING_LIMIT
        ])
        def mapper = tomlMapper(ctx)
        def toml = generateStringToml(512)

        when:
        mapper.readValue(toml, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "String value length", "exceeds the maximum allowed")

        cleanup:
        ctx.close()
    }

    void "default parser rejects oversized number tokens"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def toml = generateNumberToml(1200)

        when:
        mapper.readValue(toml, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Number value length (1200) exceeds the maximum allowed")

        cleanup:
        ctx.close()
    }

    void "configured nesting depth is projected into toml parser constraints"() {
        given:
        def ctx = ApplicationContext.run(['micronaut.serde.maximum-nesting-depth': 2])
        def mapper = tomlMapper(ctx)
        def toml = "foo = { bar = { baz = { qux = 'value' } } }\n"

        when:
        mapper.readValue(toml, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Document nesting depth", "Maximum depth exceeded")
        messageContainsAny(e, "maximum allowed (2", "maximum nesting depth")

        cleanup:
        ctx.close()
    }

    void "large but valid strings still round trip"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def value = "a" * 100_000

        when:
        def toml = mapper.writeValueAsString([foo: value])
        def parsed = mapper.readValue(toml, Argument.of(Map))

        then:
        parsed.foo == value

        cleanup:
        ctx.close()
    }

    void "configured string-length constraint rejects oversized string tokens"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': TYPED_STRING_LIMIT
        ])
        def mapper = tomlMapper(ctx)
        def toml = generateStringToml(512)

        when:
        mapper.readValue(toml, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "String value length", "exceeds the maximum allowed")

        cleanup:
        ctx.close()
    }

    void "configured string-length constraint rejects oversized typed wrapper strings"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': TYPED_STRING_LIMIT
        ])
        def mapper = tomlMapper(ctx)
        def toml = generateTypedStringToml(TYPED_STRING_LEN)

        when:
        mapper.readValue(toml, Argument.of(StringWrapper))

        then:
        Exception e = thrown()
        messageContainsAny(e, "String value length", "exceeds the maximum allowed")

        cleanup:
        ctx.close()
    }

    void "relaxed string-length constraint preserves oversized typed wrapper strings"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': 1024
        ])
        def mapper = tomlMapper(ctx)
        def toml = generateTypedStringToml(TYPED_STRING_LEN)

        when:
        def parsed = mapper.readValue(toml, Argument.of(StringWrapper))

        then:
        parsed.string.size() == TYPED_STRING_LEN

        cleanup:
        ctx.close()
    }

    void "malformed utf8 bytes fail cleanly"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        byte[] input = [0x20, (byte) 0xCD] as byte[]

        when:
        mapper.readValue(input, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Unexpected EOF in the middle of a multi-byte", "got 1, needed 2", "Invalid UTF-8")

        cleanup:
        ctx.close()
    }

    void "malformed exponent overflow fails cleanly"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)

        when:
        mapper.readValue("q=8E8188888888", Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Invalid number", "8E8188888888")

        cleanup:
        ctx.close()
    }

    @Unroll
    void "parser migration rejects #label through public mapper"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)

        when:
        mapper.readValue(toml, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, fragments as String[])

        cleanup:
        ctx.close()

        where:
        label                 | toml                              | fragments
        "duplicate keys"      | "name = 'first'\nname = 'second'" | ["Duplicate", "duplicate", "already exists"]
        "invalid escaping"    | 'name = "\\k"'                    | ["Unknown escape", "Invalid escape", "\\k"]
        "malformed numeric"   | "number = 01"                     | ["Zero-prefixed", "Invalid number", "01"]
    }

    void "truncated malformed numeric token fails cleanly"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)

        when:
        mapper.readValue("j=427\n-03b-", Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Premature end of file", "Unexpected end-of-input", "Unexpected EOF")

        cleanup:
        ctx.close()
    }

    void "deeply nested fuzz input fails with nesting depth constraints"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def input = "a={" * 9999

        when:
        mapper.readValue(input, Argument.of(Map))

        then:
        Exception e = thrown()
        e.message.contains("nesting depth") || e.message.contains("Maximum depth exceeded")

        cleanup:
        ctx.close()
    }

    void "jackson big integer fuzz reproducer fails cleanly without partial success"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def input = """a=1971
0O=0xd6e0333333243333333
033333333434"""

        when:
        mapper.readValue(input, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Premature end of file", "Unexpected end-of-input", "Unexpected EOF")

        cleanup:
        ctx.close()
    }

    void "vendored jackson codepoint fuzz reproducer fails cleanly"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def input = testResourceBase64Bytes("jackson-codepoint-51654.base64")

        when:
        mapper.readValue(new ByteArrayInputStream(input), Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "EOF in wrong state", "Premature end of file", "Unexpected end-of-input", "Unexpected EOF", "Invalid UTF-8")

        cleanup:
        ctx.close()
    }

    void "vendored jackson inline table fuzz reproducer fails with nesting depth constraints"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def input = testResourceBytes("jackson-inline-table-depth-6370486359031808.toml")

        when:
        mapper.readValue(input, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Document nesting depth", "nesting depth", "Maximum depth exceeded")

        cleanup:
        ctx.close()
    }

    void "vendored jackson array copy fuzz reproducer fails cleanly from streams"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def input = testResourceBytes("jackson-array-copy-6542204348006400.toml")

        when:
        mapper.readValue(new ByteArrayInputStream(input), Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Premature end of file", "Unexpected end-of-input", "Unexpected EOF", "Invalid UTF-8")

        cleanup:
        ctx.close()
    }

    void "default parser rejects too long decimal tokens with truncated messaging"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def toml = "foo = 0.${'0' * 10_000}1"

        when:
        mapper.readValue(toml, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "[truncated]", "Number value length", "exceeds the maximum allowed")

        cleanup:
        ctx.close()
    }

    void "relaxed number-length constraint preserves very long decimal tokens through the micronaut mapper"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-number-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def toml = "foo = 0.${'0' * 10_000}1"

        when:
        def parsed = mapper.readValue(toml, Argument.of(Map))
        BigDecimal decimal = parsed.foo as BigDecimal

        then:
        decimal > BigDecimal.ZERO
        decimal < BigDecimal.ONE

        cleanup:
        ctx.close()
    }

    void "relaxed number-length constraint preserves very long binary integers through the micronaut mapper"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-number-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def scale = 10_000
        def toml = "foo = 0b1${'0' * scale}"

        when:
        def parsed = mapper.readValue(toml, Argument.of(Map))
        BigInteger integer = parsed.foo as BigInteger

        then:
        integer.bitLength() == scale + 1

        cleanup:
        ctx.close()
    }

    void "larger configured string-length constraint preserves strings that exceed a smaller configured limit"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': 1024
        ])
        def mapper = tomlMapper(ctx)
        def toml = generateStringToml(512)

        when:
        def parsed = mapper.readValue(toml, Argument.of(Map))

        then:
        parsed.foo.size() == 512

        cleanup:
        ctx.close()
    }

    void "relaxed parser safely handles very long comments through the micronaut mapper"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def toml = "# ${'a' * 10_000}"

        expect:
        mapper.readValue(toml, Argument.of(Map)).isEmpty()

        cleanup:
        ctx.close()
    }

    void "relaxed parser safely handles very long array whitespace through the micronaut mapper"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def toml = "foo = [${' ' * 10_000}]"

        when:
        def parsed = mapper.readValue(toml, Argument.of(Map))

        then:
        parsed.foo.size() == 0

        cleanup:
        ctx.close()
    }

    void "relaxed parser preserves very long unquoted keys through the micronaut mapper"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def key = "f${'o' * 10_000}"
        def toml = "${key} = 0"

        when:
        def parsed = mapper.readValue(toml, Argument.of(Map))

        then:
        parsed.keySet().first() == key

        cleanup:
        ctx.close()
    }

    void "relaxed string-length constraint decodes very long escaped strings through the micronaut mapper"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def toml = "foo = \"${'\\n' * 10_000}\""

        when:
        def parsed = mapper.readValue(toml, Argument.of(Map))

        then:
        parsed.foo.length() == 10_000

        cleanup:
        ctx.close()
    }

    void "relaxed number length does not constrain strings"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-number-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def toml = generateStringToml(100_000)

        expect:
        mapper.readValue(toml, Argument.of(Map)).foo.size() == 100_000

        cleanup:
        ctx.close()
    }

    void "relaxed string length does not change default number limits"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': Integer.MAX_VALUE
        ])
        def mapper = tomlMapper(ctx)
        def toml = generateNumberToml(1200)

        when:
        mapper.readValue(toml, Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Number value length (1200) exceeds the maximum allowed")

        cleanup:
        ctx.close()
    }

    void "tree reads are not affected by toml read constraints"() {
        given:
        def ctx = ApplicationContext.run([
            'micronaut.serde.toml.read-constraints.max-string-length': 16
        ])
        def mapper = tomlMapper(ctx)
        def largeValue = 'a' * 128
        def tree = JsonNode.createObjectNode([
            foo: JsonNode.createStringNode(largeValue)
        ])

        expect:
        mapper.readValueFromTree(tree, Argument.of(Map)).foo == largeValue

        cleanup:
        ctx.close()
    }

    void "buffer boundary regression preserves following tokens"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def bufferLength = 4096
        def toml = """foo = "${'a' * (bufferLength - 19)}"
bar = 123
baz = "${'a' * bufferLength}"
"""

        when:
        def parsed = mapper.readValue(toml, Argument.of(Map))

        then:
        parsed.bar == 123
        parsed.foo.size() == bufferLength - 19
        parsed.baz.size() == bufferLength

        cleanup:
        ctx.close()
    }

    void "control characters inside comments fail cleanly"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)

        when:
        mapper.readValue("a = \"0x7f\" # \u007F", Argument.of(Map))

        then:
        Exception e = thrown()
        messageContainsAny(e, "Illegal control character")

        cleanup:
        ctx.close()
    }

    void "utf8 emoji crossing buffer boundaries parses safely from bytes and streams"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def filler = 'a' * 4088
        def expected = "${filler}\uD83D\uDE00"
        def toml = "foo = \"${expected}\"\nbar = 1\n"
        def bytes = toml.bytes

        when:
        def fromBytes = mapper.readValue(bytes, Argument.of(Map))
        def fromStream = mapper.readValue(new ByteArrayInputStream(bytes), Argument.of(Map))

        then:
        fromBytes.foo == expected
        fromBytes.bar == 1
        fromStream.foo == expected
        fromStream.bar == 1

        cleanup:
        ctx.close()
    }

    void "utf8 emoji crossing buffer boundaries survives one-byte throttled streams"() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = tomlMapper(ctx)
        def filler = 'a' * 4088
        def expected = "${filler}\uD83D\uDE00"
        def toml = "foo = \"${expected}\"\nbar = 1\n"
        def bytes = toml.bytes
        def input = new InputStream() {
            private int index = 0

            @Override
            int read() {
                index < bytes.length ? bytes[index++] & 0xFF : -1
            }

            @Override
            int read(byte[] b, int off, int len) {
                if (index >= bytes.length) {
                    return -1
                }
                b[off] = bytes[index++]
                return 1
            }
        }

        when:
        def parsed = mapper.readValue(input, Argument.of(Map))

        then:
        parsed.foo == expected
        parsed.bar == 1

        cleanup:
        ctx.close()
    }

    private static ObjectMapper tomlMapper(ApplicationContext ctx) {
        ctx.getBean(ObjectMapper, Qualifiers.byName("toml"))
    }

    private static String generateStringToml(int len) {
        new StringBuilder(len)
            .append("foo = \"")
            .append("a" * len)
            .append('"')
            .append('\n')
            .toString()
    }

    private static String generateNumberToml(int len) {
        new StringBuilder(len)
            .append("foo = ")
            .append("1" * len)
            .append('\n')
            .toString()
    }

    private static String generateTypedStringToml(int len) {
        new StringBuilder(len)
            .append("string = \"")
            .append("a" * len)
            .append('"')
            .toString()
    }

    private static byte[] testResourceBytes(String name) {
        def stream = TomlLimitSpec.getResourceAsStream("/io/micronaut/serde/toml/fuzz/${name}")
        assert stream != null: "Missing test resource ${name}"
        stream.bytes
    }

    private static byte[] testResourceBase64Bytes(String name) {
        Base64.decoder.decode(testResourceText(name).replaceAll("\\s", ""))
    }

    private static String testResourceText(String name) {
        def stream = TomlLimitSpec.getResourceAsStream("/io/micronaut/serde/toml/fuzz/${name}")
        assert stream != null: "Missing test resource ${name}"
        stream.getText("UTF-8")
    }

    private static void messageContainsAny(Throwable e, String... fragments) {
        Throwable current = e
        while (current != null) {
            if (current.message != null && fragments.any { current.message.contains(it) }) {
                return
            }
            current = current.cause
        }
        assert false: "Expected one of ${fragments as List} in exception chain, but got ${messageChain(e)}"
    }

    private static String messageChain(Throwable e) {
        List<String> messages = []
        Throwable current = e
        while (current != null) {
            messages << "${current.class.simpleName}: ${current.message}"
            current = current.cause
        }
        messages.join(" -> ")
    }
}
