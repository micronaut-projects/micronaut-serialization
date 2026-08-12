package io.micronaut.serde.xml.woodstox

import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.AbstractXmlCollectionSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject

@MicronautTest
class WoodstoxXmlCollectionSpec extends AbstractXmlCollectionSpec implements WoodstoxXmlSpec, TestPropertyProvider {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    Map<String, String> getProperties() {
        ["micronaut.serde.write-binary-as-array": "false"]
    }
}
