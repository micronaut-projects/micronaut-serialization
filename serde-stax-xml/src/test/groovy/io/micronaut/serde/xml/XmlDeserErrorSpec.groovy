package io.micronaut.serde.xml

import io.micronaut.json.JsonSyntaxException
import io.micronaut.serde.xml.tck.AbstractXmlDeserErrorSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class XmlDeserErrorSpec extends AbstractXmlDeserErrorSpec implements MicronautXmlSpec {

    @Inject
    XmlObjectMapper xmlMapper

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }

}
