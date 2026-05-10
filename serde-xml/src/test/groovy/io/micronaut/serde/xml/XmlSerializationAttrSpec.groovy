package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractXmlSerializationAttrSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlSerializationAttrSpec extends AbstractXmlSerializationAttrSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
