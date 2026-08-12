package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractXmlDeserErrorSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlDeserErrorSpec extends AbstractXmlDeserErrorSpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
