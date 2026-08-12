package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractXmlNamespaceSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlNamespaceSpec extends AbstractXmlNamespaceSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
