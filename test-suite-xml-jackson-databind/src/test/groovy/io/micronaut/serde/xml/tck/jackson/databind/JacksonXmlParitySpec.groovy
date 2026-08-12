package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.AbstractJacksonXmlParitySpec
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlMapper

/**
 * Runs the portable Jackson XML parity scenarios against Jackson Dataformat XML.
 *
 * @since 3.2
 */
class JacksonXmlParitySpec extends AbstractJacksonXmlParitySpec implements JacksonDatabindXmlSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = XmlMapper.builder().build()

    @Override
    XmlMapper getDatabindXmlMapper() {
        return jacksonXmlMapper
    }
}
