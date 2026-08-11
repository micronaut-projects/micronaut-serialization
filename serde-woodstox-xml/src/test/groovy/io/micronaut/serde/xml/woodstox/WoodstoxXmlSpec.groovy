package io.micronaut.serde.xml.woodstox

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.serde.xml.XmlObjectMapper
import io.micronaut.serde.xml.tck.XmlSpec

import java.nio.charset.StandardCharsets

trait WoodstoxXmlSpec implements XmlSpec {

    abstract XmlObjectMapper getXmlMapper()

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

    def <T> T readXml(String xml, Class<T> type) {
        xmlMapper.readValue(xml, type)
    }

    @Override
    def <T> T readXmlWithProperties(Map<String, Object> properties, String xml, Class<T> type) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            return context.getBean(XmlObjectMapper).readValue(xml, type)
        } finally {
            context.close()
        }
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
