package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractJacksonXmlParitySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlJacksonParitySpec extends AbstractJacksonXmlParitySpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
