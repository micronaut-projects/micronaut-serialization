package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractJacksonXmlParitySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 * Runs the portable Jackson XML parity scenarios against Micronaut Serialization XML.
 *
 * @since 3.2
 */
@MicronautTest
class XmlJacksonParitySpec extends AbstractJacksonXmlParitySpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
