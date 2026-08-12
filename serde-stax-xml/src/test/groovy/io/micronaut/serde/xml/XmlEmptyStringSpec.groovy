package io.micronaut.serde.xml

import io.micronaut.serde.xml.tck.AbstractXmlEmptyStringSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlEmptyStringSpec extends AbstractXmlEmptyStringSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        xmlMapper
    }

}
