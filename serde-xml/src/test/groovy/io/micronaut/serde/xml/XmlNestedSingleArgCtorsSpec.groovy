package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractNestedSingleArgCtorsSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlNestedSingleArgCtorsSpec extends AbstractNestedSingleArgCtorsSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
