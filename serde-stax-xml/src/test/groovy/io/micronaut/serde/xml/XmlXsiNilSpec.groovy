package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractXsiNilSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlXsiNilSpec extends AbstractXsiNilSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }
}
