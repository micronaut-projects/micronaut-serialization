package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.SerializationAttrSpec
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.XmlFactory
import org.codehaus.stax2.XMLOutputFactory2
import javax.xml.stream.XMLOutputFactory

class JacksonXmlSerializationAttrSpec extends SerializationAttrSpec implements JacksonDatabindXmlSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = createMapper()

    private static XmlMapper createMapper() {
        // 1. Create a StAX output factory and disable self-closing tags
        XMLOutputFactory2 staxFactory = (XMLOutputFactory2) XMLOutputFactory.newFactory()
        staxFactory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, false)

        // 2. Build the Jackson XmlFactory using the custom StAX factory
        XmlFactory xmlFactory = XmlFactory.builder()
                .xmlOutputFactory(staxFactory)
                .build()

        // 3. Build the mapper with the custom factory
        return XmlMapper.builder(xmlFactory).build()
    }

    @Override
    XmlMapper getDatabindXmlMapper() {
        return jacksonXmlMapper
    }
}
