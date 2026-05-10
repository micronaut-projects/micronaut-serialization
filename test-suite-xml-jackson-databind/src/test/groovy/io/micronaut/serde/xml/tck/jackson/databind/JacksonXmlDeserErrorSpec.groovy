package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.AbstractXmlDeserErrorSpec
import spock.lang.Shared
import tools.jackson.core.exc.StreamReadException
import tools.jackson.dataformat.xml.XmlMapper

class JacksonXmlDeserErrorSpec extends AbstractXmlDeserErrorSpec implements JacksonDatabindXmlSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = XmlMapper.builder().build()

    @Override
    XmlMapper getDatabindXmlMapper() {
        return jacksonXmlMapper
    }

}
