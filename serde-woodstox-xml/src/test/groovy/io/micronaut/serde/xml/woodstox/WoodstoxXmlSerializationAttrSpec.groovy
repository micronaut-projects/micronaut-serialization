package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractXmlSerializationAttrSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlSerializationAttrSpec extends AbstractXmlSerializationAttrSpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
