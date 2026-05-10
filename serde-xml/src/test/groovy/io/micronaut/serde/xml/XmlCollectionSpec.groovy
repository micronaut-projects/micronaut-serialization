package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractXmlCollectionSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlCollectionSpec extends AbstractXmlCollectionSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
