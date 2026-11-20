package io.micronaut.serde.yaml

import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.LimitingStream
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class YamlEncoderSpec extends Specification {

    @Inject
    YamlObjectMapper mapper

    def "documents end every line with a line feed on any platform"() {
        expect:
        mapper.writeValueAsString([a: 1, b: [x: "z"]]) == "a: 1\nb:\n  x: z\n"
        !mapper.writeValueAsString(["multi\nline"]).contains("\r")
    }

    def "floats are written with float precision, not the widened double"() {
        expect:
        mapper.writeValueAsString([v: 0.1f]) == "v: 0.1\n"
        mapper.writeValueAsString([v: 1.1f]) == "v: 1.1\n"
        mapper.writeValueAsString([v: 0.1d]) == "v: 0.1\n"
    }

    def "non-finite floats and doubles use the yaml notation"() {
        expect:
        mapper.writeValueAsString([v: value]) == "v: " + written + "\n"

        where:
        value                       || written
        Float.NaN                   || ".nan"
        Float.POSITIVE_INFINITY     || ".inf"
        Float.NEGATIVE_INFINITY     || "-.inf"
        Double.NaN                  || ".nan"
        Double.POSITIVE_INFINITY    || ".inf"
        Double.NEGATIVE_INFINITY    || "-.inf"
    }

    def "chars are written like strings"() {
        expect:
        mapper.writeValueAsString([c: 'a' as char]) == "c: a\n"
        mapper.writeValueAsString([c: ':' as char]) == "c: \":\"\n"
        mapper.readValue("c: a\n", Argument.mapOf(String, Character)).c == ('a' as char)
    }

    def "closing a nested child encoder finishes only its own structure (#1505)"() {
        given:
        def out = new ByteArrayOutputStream()
        def encoder = new YamlEncoder(out, LimitingStream.DEFAULT_LIMITS)

        when: "serializers close the child encoders they open, as they do for a list in a map value"
        def root = encoder.encodeObject(Argument.OBJECT_ARGUMENT)
        root.encodeKey("nested")
        try (Encoder nested = root.encodeObject(Argument.OBJECT_ARGUMENT)) {
            nested.encodeKey("list")
            try (Encoder list = nested.encodeArray(Argument.OBJECT_ARGUMENT)) {
                list.encodeString("a")
            }
            nested.encodeKey("after")
            nested.encodeInt(1)
        }
        root.encodeKey("type")
        root.encodeString("x")
        root.close()

        then:
        out.toString("UTF-8") == "nested:\n  list:\n  - a\n  after: 1\ntype: x\n"
    }

    def "closing the root encoder completes structures a serializer left open"() {
        given:
        def out = new ByteArrayOutputStream()
        def encoder = new YamlEncoder(out, LimitingStream.DEFAULT_LIMITS)

        when:
        def root = encoder.encodeObject(Argument.OBJECT_ARGUMENT)
        root.encodeKey("nested")
        def nested = root.encodeArray(Argument.OBJECT_ARGUMENT)
        nested.encodeString("a")
        encoder.close()

        then:
        out.toString("UTF-8") == "nested:\n- a\n"
    }

    def "closing the encoder itself completes and flushes every structure that is still open"() {
        given:
        def out = new ByteArrayOutputStream()

        when: "a caller of the public encoder lets try-with-resources close it with structures open"
        try (YamlEncoder encoder = new YamlEncoder(out, LimitingStream.DEFAULT_LIMITS)) {
            def root = encoder.encodeObject(Argument.OBJECT_ARGUMENT)
            root.encodeKey("nested")
            def nested = root.encodeArray(Argument.OBJECT_ARGUMENT)
            nested.encodeString("a")
        }

        then:
        out.toString("UTF-8") == "nested:\n- a\n"
    }

    def "a child encoder closed after finishing its structure does not finish its parent"() {
        given:
        def out = new ByteArrayOutputStream()
        def encoder = new YamlEncoder(out, LimitingStream.DEFAULT_LIMITS)

        when:
        def root = encoder.encodeObject(Argument.OBJECT_ARGUMENT)
        root.encodeKey("list")
        try (Encoder list = root.encodeArray(Argument.OBJECT_ARGUMENT)) {
            list.encodeString("a")
            list.finishStructure()
        }
        root.encodeKey("after")
        root.encodeInt(1)
        root.finishStructure()

        then:
        out.toString("UTF-8") == "list:\n- a\nafter: 1\n"
    }

    def "a serializer that fails still leaves the finished partial document in the output"() {
        given:
        def out = new ByteArrayOutputStream()
        def encoder = new YamlEncoder(out, LimitingStream.DEFAULT_LIMITS)

        when: "the mapper closes the root encoder when serialization throws"
        try (encoder) {
            def outer = encoder.encodeArray(Argument.OBJECT_ARGUMENT)
            def inner = outer.encodeArray(Argument.OBJECT_ARGUMENT)
            inner.encodeInt(1)
            throw new IOException("boom")
        }

        then:
        thrown(IOException)
        out.toString("UTF-8") == "- - 1\n"
    }
}
