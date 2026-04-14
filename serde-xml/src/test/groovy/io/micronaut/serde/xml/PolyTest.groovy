package io.micronaut.serde.xml

import io.micronaut.json.JsonMapper
import io.micronaut.serde.xml.tck.EmptyPolymorphicTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class PolyTest extends EmptyPolymorphicTest {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    JsonMapper getXmlMapper() {
        return xmlMapper
    }
}
