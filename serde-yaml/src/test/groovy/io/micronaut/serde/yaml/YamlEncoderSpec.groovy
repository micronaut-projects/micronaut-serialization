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

    def "chars are written like strings"() {
        expect:
        mapper.writeValueAsString([c: 'a' as char]) == "c: a\n"
        mapper.writeValueAsString([c: ':' as char]) == "c: \":\"\n"
        mapper.readValue("c: a\n", Argument.mapOf(String, Character)).c == ('a' as char)
    }
}
