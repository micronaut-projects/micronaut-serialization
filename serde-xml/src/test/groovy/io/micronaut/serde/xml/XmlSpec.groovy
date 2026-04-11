package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.DeserializerLocator
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.config.annotation.SerdeConfig
import tools.jackson.databind.JsonNode
import tools.jackson.dataformat.xml.XmlMapper

import java.nio.charset.StandardCharsets

trait XmlSpec {


    abstract XmlObjectMapper getXmlMapper()


    String writeXml(Object bean) {
        new String(getXmlMapper().writeValueAsBytes(bean), StandardCharsets.UTF_8)
    }

    String writeXml(Argument argument, Object bean) {
        new String(getXmlMapper().writeValueAsBytes(argument, bean), StandardCharsets.UTF_8)
    }

    /**
     * Converting JSON string to XML with Jackson's JsonNode.
     */
    String xmlString(String json) {
        JsonNode tree = JACKSON_JSON.readTree(json)
        return JACKSON_XML.writeValueAsString(tree)
    }

    byte[] xmlBytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8)
    }

    boolean XmlMatches(String result, String expected) {
        result == expected
    }

    boolean objRepresentationMatches(Object obj, String xml2) {
        def xml1 = xmlMapper.writeValueAsBytes(obj)
        def xml1_string = new String(xml1, StandardCharsets.UTF_8)
        assert xml1_string == xml2
        xml1_string == xml2
    }

    def <T> T serializeDeserialize(T obj) {
        return serializeDeserializeAs(obj, Argument.of(obj.getClass()))
    }

    def <T> T serializeDeserializeAs(T obj, Argument type) {
        def output = getXmlMapper().writeValueAsBytes(obj)
        return getXmlMapper().readValue(output, type) as T
    }
}
