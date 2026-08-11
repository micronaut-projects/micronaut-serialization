package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractXmlEmptyStringSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlEmptyStringSpec extends AbstractXmlEmptyStringSpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
