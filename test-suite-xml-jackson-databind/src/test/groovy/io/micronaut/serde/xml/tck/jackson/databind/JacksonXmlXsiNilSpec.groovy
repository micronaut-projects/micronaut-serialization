package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.AbstractXsiNilSpec
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlMapper

class JacksonXmlXsiNilSpec extends AbstractXsiNilSpec implements JacksonDatabindXmlSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = XmlMapper.builder().build()

    @Override
    XmlMapper getDatabindXmlMapper() {
        return jacksonXmlMapper
    }
}
