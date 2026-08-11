package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractXmlEmptyPolymorphicSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlEmptyPolymorphicSpec extends AbstractXmlEmptyPolymorphicSpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
