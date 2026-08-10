package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
class XmlObjectMapperRegressionSpec extends Specification {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    XmlObjectMapper xmlMapper

    def "root scalar values have a valid document element"() {
        when:
        def xml = xmlMapper.writeValueAsString("hello")

        then:
        xml == "<String>hello</String>"
        xmlMapper.readValue(xml, String) == "hello"
    }

    def "typed null values do not reach the selected serializer"() {
        expect:
        new String(xmlMapper.writeValueAsBytes(Argument.of(String), null)).endsWith("<null/>")
        xmlMapper.writeValueToTree(Argument.of(String), null) == JsonNode.nullNode()
    }

    def "null collection entries do not close the collection wrapper"() {
        expect:
        xmlMapper.writeValueAsString(["a", null, "b"]) ==
            "<ArrayList><item>a</item><item/><item>b</item></ArrayList>"
    }

    def "external XML entities are rejected"() {
        given:
        def xml = '''<!DOCTYPE ExternalEntityBean [
            <!ENTITY external SYSTEM "file:///etc/passwd">
        ]>
        <ExternalEntityBean><value>&external;</value></ExternalEntityBean>'''

        when:
        xmlMapper.readValue(xml, ExternalEntityBean)

        then:
        thrown(SerdeException)
    }

    @Serdeable
    static class ExternalEntityBean {
        String value
    }
}
