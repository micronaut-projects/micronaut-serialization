package io.micronaut.serde.toml

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
class TomlSerializationInclusionSpec extends Specification implements TestPropertyProvider {

    @Inject
    @Named("toml")
    ObjectMapper tomlMapper

    @Override
    Map<String, String> getProperties() {
        ["micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS.name()]
    }

    void "always inclusion writes empty strings"() {
        given:
        def bean = new TextBean(text: "")

        when:
        def toml = tomlMapper.writeValueAsString(bean)
        def decoded = tomlMapper.readValue(toml, Argument.of(TextBean))

        then:
        toml == "text = ''\n"
        decoded.text == ""
    }

    void "always inclusion writes empty byte arrays as base64 strings"() {
        given:
        def bean = new BinaryBean(data: new byte[0])

        when:
        def toml = tomlMapper.writeValueAsString(bean)
        def decoded = tomlMapper.readValue(toml, Argument.of(BinaryBean))

        then:
        toml == "data = ''\n"
        decoded.data == new byte[0]
    }

    void "always inclusion still omits null values because toml has no null"() {
        given:
        def bean = new TextBean(text: null)

        when:
        def toml = tomlMapper.writeValueAsString(bean)
        def decoded = tomlMapper.readValue(toml, Argument.of(TextBean))

        then:
        toml == ""
        decoded.text == null
    }

    @Serdeable
    static class TextBean {
        String text
    }

    @Serdeable
    static class BinaryBean {
        byte[] data
    }
}
