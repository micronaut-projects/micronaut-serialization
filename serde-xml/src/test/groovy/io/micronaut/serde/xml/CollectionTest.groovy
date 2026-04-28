package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.JavaCollectionsTestSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class CollectionTest extends JavaCollectionsTestSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
