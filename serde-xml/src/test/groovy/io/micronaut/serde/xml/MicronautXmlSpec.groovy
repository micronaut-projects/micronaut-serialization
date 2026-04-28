package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper

import java.io.InputStream
import java.nio.charset.StandardCharsets

trait MicronautXmlSpec implements io.micronaut.serde.xml.tck.XmlSpec {

    abstract ObjectMapper getXmlMapper()

    @Override
    def <T> T readXml(String xml, Argument<T> type) {
        xmlMapper.readValue(xml, type)
    }

    @Override
    def <T> T readXml(byte[] xml, Argument<T> type) {
        xmlMapper.readValue(xml, type)
    }

    @Override
    def <T> T readXml(InputStream xml, Argument<T> type) {
        xmlMapper.readValue(xml, type)
    }

    @Override
    String writeXml(Object bean) {
        xmlMapper.writeValueAsString(bean)
    }

    @Override
    String writeXml(Argument<?> argument, Object bean) {
        new String(writeXmlAsBytes(argument, bean), StandardCharsets.UTF_8)
    }

    @Override
    byte[] writeXmlAsBytes(Object bean) {
        xmlMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writeXmlAsBytes(Argument<?> argument, Object bean) {
        xmlMapper.writeValueAsBytes(argument, bean)
    }
}
