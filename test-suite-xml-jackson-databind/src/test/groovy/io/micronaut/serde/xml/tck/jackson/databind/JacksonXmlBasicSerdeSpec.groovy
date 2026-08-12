package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.AbstractBasicSerdeSpec
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.XmlReadFeature

class JacksonXmlBasicSerdeSpec extends AbstractBasicSerdeSpec implements JacksonDatabindXmlSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = XmlMapper.builder().build()

    @Shared
    private final XmlMapper emptyElementAsNullXmlMapper = XmlMapper.builder()
            .enable(XmlReadFeature.EMPTY_ELEMENT_AS_NULL)
            .build()

    @Override
    XmlMapper getDatabindXmlMapper() {
        return jacksonXmlMapper
    }

    @Override
    def <T> T readXml(String xml, Class<T> type) {
        emptyElementAsNullXmlMapper.readValue(xml, type)
    }

    @Override
    def <T> T readXml(byte[] xml, Class<T> type) {
        emptyElementAsNullXmlMapper.readValue(xml, type)
    }

    @Override
    def <T> T readXml(InputStream xml, Class<T> type) {
        emptyElementAsNullXmlMapper.readValue(xml, type)
    }
}
