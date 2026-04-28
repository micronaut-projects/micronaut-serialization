package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.EmptyPolymorphicTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class PolyTest extends EmptyPolymorphicTest implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
