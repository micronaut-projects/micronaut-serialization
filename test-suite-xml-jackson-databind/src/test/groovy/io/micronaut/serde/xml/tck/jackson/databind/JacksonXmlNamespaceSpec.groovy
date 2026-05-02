package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.AbstractNamespaceSpec
import org.codehaus.stax2.XMLOutputFactory2
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper

import javax.xml.stream.XMLOutputFactory

class JacksonXmlNamespaceSpec extends AbstractNamespaceSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = createMapper()

    private static XmlMapper createMapper() {
        XMLOutputFactory2 staxFactory = (XMLOutputFactory2) XMLOutputFactory.newFactory()
        staxFactory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, false)
        XmlFactory xmlFactory = XmlFactory.builder()
                .xmlOutputFactory(staxFactory)
                .build()
        return XmlMapper.builder(xmlFactory).build()
    }

    @Override
    Object getXmlMapper() {
        return jacksonXmlMapper
    }
}
