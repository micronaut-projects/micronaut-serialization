package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.serde.xml.tck.JavaCollectionsTestSpec
import spock.lang.Shared
import tools.jackson.dataformat.xml.XmlMapper

class JacksonXmlCollectionSpec extends JavaCollectionsTestSpec {

    @Shared
    private final XmlMapper jacksonXmlMapper = XmlMapper.builder().build()

    @Override
    Object getXmlMapper() {
        return jacksonXmlMapper
    }
}
