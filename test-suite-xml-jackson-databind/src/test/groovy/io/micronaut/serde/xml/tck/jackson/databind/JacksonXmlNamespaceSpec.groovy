package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.AbstractXmlNamespaceSpec
import org.codehaus.stax2.XMLOutputFactory2
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlFactory
import tools.jackson.dataformat.xml.XmlMapper

import javax.xml.stream.XMLOutputFactory

class JacksonXmlNamespaceSpec extends AbstractXmlNamespaceSpec implements JacksonDatabindXmlSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = createMapper()

    /**
     * The TCK's namespace assertions expect the open/close form
     * ({@code <X></X>}) for content-less elements. Jackson's default Stax
     * output factory ({@code Woodstox}) emits the self-closing form
     * ({@code <X/>}) unless {@code P_AUTOMATIC_EMPTY_ELEMENTS} is turned off,
     * so we wire a custom factory here to match the spec.
     */
    private static XmlMapper createMapper() {
        XMLOutputFactory2 staxFactory = (XMLOutputFactory2) XMLOutputFactory.newFactory()
        staxFactory.setProperty(XMLOutputFactory2.P_AUTOMATIC_EMPTY_ELEMENTS, false)
        XmlFactory xmlFactory = XmlFactory.builder()
                .xmlOutputFactory(staxFactory)
                .build()
        return XmlMapper.builder(xmlFactory).build()
    }

    @Override
    XmlMapper getDatabindXmlMapper() {
        return jacksonXmlMapper
    }
}
