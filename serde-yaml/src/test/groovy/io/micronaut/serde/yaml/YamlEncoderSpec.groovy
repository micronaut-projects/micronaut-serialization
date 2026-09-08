package io.micronaut.serde.yaml

import io.micronaut.core.type.Argument
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
}
