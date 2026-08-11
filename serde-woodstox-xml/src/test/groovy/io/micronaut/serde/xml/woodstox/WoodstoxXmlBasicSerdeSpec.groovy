package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named

@MicronautTest
class WoodstoxXmlBasicSerdeSpec extends AbstractBasicSerdeSpec implements WoodstoxXmlSpec {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    XmlObjectMapper xmlMapper
}
