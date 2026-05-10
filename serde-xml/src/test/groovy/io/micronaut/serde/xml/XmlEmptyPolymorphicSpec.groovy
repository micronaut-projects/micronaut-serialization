package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractXmlEmptyPolymorphicSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlEmptyPolymorphicSpec extends AbstractXmlEmptyPolymorphicSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
