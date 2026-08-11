package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.SerdeableGenerated
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
class XmlSerializationInclusionSpec extends Specification implements TestPropertyProvider {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    ObjectMapper xmlMapper

    @Override
    Map<String, String> getProperties() {
        [
                "micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS.name(),
                "micronaut.serde.write-binary-as-array"   : "false"
        ]
    }

    void "always inclusion writes empty strings"() {
        given:
        def bean = new TextBean(text: "")

        when:
        def xml = xmlMapper.writeValueAsString(bean)
        def decoded = xmlMapper.readValue(xml, Argument.of(TextBean))

        then:
        xml == "<TextBean><text></text></TextBean>"
        decoded.text == ""
    }

    void "always inclusion writes empty byte arrays as base64 strings"() {
        given:
        def bean = new BinaryBean(data: new byte[0])

        when:
        def xml = xmlMapper.writeValueAsString(bean)
        def decoded = xmlMapper.readValue(xml, Argument.of(BinaryBean))

        then:
        xml == "<BinaryBean><data></data></BinaryBean>"
        decoded.data == new byte[0]
    }

    @SerdeableGenerated(skip = true)
    static class TextBean {
        String text
    }

    @SerdeableGenerated(skip = true)
    static class BinaryBean {
        byte[] data
    }
}
