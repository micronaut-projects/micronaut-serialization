package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractXsiNilSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlXsiNilSpec extends AbstractXsiNilSpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
