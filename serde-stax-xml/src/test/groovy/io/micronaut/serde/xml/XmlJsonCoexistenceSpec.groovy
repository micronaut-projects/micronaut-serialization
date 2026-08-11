package io.micronaut.serde.xml

import io.micronaut.json.tree.JsonNode
import io.micronaut.serde.annotation.SerdeableGenerated
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty

@MicronautTest
class XmlJsonCoexistenceSpec extends Specification {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    XmlObjectMapper xmlMapper

    def "XML property metadata does not replace JSON property serdes"() {
        given:
        def bean = new MixedFormatBean(
            attribute: "a",
            items: ["one", "two"],
            nested: new NestedBean(value: "v")
        )

        when:
        def tree = xmlMapper.writeValueToTree(bean)
        def decoded = xmlMapper.readValueFromTree(tree, MixedFormatBean)

        then:
        tree == JsonNode.from([
            attribute: "a",
            items: ["one", "two"],
            nested: [value: "v"]
        ])
        decoded.attribute == "a"
        decoded.items == ["one", "two"]
        decoded.nested.value == "v"
    }

    @SerdeableGenerated(skip = true)
    static class MixedFormatBean {
        @JacksonXmlProperty(isAttribute = true)
        String attribute

        @JacksonXmlElementWrapper(localName = "items")
        List<String> items

        @JacksonXmlProperty(namespace = "urn:test", localName = "nested")
        NestedBean nested
    }

    @SerdeableGenerated(skip = true)
    static class NestedBean {
        String value
    }
}
