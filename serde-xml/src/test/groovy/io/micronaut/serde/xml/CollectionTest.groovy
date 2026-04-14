package io.micronaut.serde.xml

import io.micronaut.json.JsonMapper
import io.micronaut.serde.xml.tck.JavaCollectionsTestSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class CollectionTest extends JavaCollectionsTestSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    JsonMapper getXmlMapper() {
        return xmlMapper
    }
}
