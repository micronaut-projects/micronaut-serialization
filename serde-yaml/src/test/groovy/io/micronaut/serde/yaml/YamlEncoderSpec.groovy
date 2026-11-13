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
}
