package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractXmlNamespaceSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlNamespaceSpec extends AbstractXmlNamespaceSpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
