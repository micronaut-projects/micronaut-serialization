package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractNestedSingleArgCtorsSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlNestedSingleArgCtorsSpec extends AbstractNestedSingleArgCtorsSpec implements WoodstoxXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper
}
