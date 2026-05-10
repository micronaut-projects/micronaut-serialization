package io.micronaut.serde.xml

import io.micronaut.json.JsonMapper
import io.micronaut.serde.xml.tck.AbstractBasicSerdeSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named

@MicronautTest
class XmlBasicSerdeSpec extends AbstractBasicSerdeSpec implements MicronautXmlSpec {

    @Inject
    @Named("xml")
    XmlObjectMapper xmlMapper;

    @Override
    XmlObjectMapper getXmlMapper() {
        return xmlMapper
    }





}
