package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractXmlCollectionSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class XmlCollectionSpec extends AbstractXmlCollectionSpec implements MicronautXmlSpec, TestPropertyProvider {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    Map<String, String> getProperties() {
        ["micronaut.serde.write-binary-as-array": "false"]
    }

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
