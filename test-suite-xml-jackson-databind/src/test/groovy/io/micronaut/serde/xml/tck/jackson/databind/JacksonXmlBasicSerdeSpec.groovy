package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.AbstractBasicSerdeSpec
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlMapper

class JacksonXmlBasicSerdeSpec extends AbstractBasicSerdeSpec implements JacksonDatabindXmlSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = XmlMapper.builder().build()

    @Override
    XmlMapper getDatabindXmlMapper() {
        return jacksonXmlMapper
    }
}
