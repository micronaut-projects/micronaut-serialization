package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.xml.tck.XmlSpec
import tools.jackson.databind.JavaType
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.XmlReadFeature

import java.io.InputStream

trait JacksonDatabindXmlSpec implements XmlSpec {

    abstract XmlMapper getDatabindXmlMapper()

    @Override
    def <T> T readXml(String xml, Argument<T> type) {
        databindXmlMapper.readValue(xml, toJavaType(type))
    }

    @Override
    def <T> T readXml(byte[] xml, Argument<T> type) {
        databindXmlMapper.readValue(xml, toJavaType(type))
    }

    @Override
    def <T> T readXml(InputStream xml, Argument<T> type) {
        databindXmlMapper.readValue(xml, toJavaType(type))
    }

    /**
     * Interprets Micronaut-style configuration property keys and applies their
     * Jackson equivalent on a fresh {@link XmlMapper}. Only the
     * {@code micronaut.serde.xml.xml-read-features.<NAME>} namespace is mapped
     * — the {@code <NAME>} segment is resolved to {@link XmlReadFeature}.
     * Other keys are silently ignored (no Jackson counterpart).
     */
    @Override
    def <T> T readXmlWithProperties(Map<String, Object> properties, String xml, Class<T> type) {
        def builder = XmlMapper.builder()
        String prefix = "micronaut.serde.xml.xml-read-features."
        properties.each { key, value ->
            if (key.startsWith(prefix)) {
                String name = key.substring(prefix.length())
                try {
                    XmlReadFeature feature = XmlReadFeature.valueOf(name)
                    if (value == true || value == "true") {
                        builder.enable(feature)
                    } else {
                        builder.disable(feature)
                    }
                } catch (IllegalArgumentException ignored) {
                    // Unknown feature name — ignore so the TCK stays robust.
                }
            }
        }
        return builder.build().readValue(xml, type)
    }

    @Override
    String writeXml(Object bean) {
        databindXmlMapper.writeValueAsString(bean)
    }

    @Override
    String writeXml(Argument<?> argument, Object bean) {
        databindXmlMapper.writerFor(toJavaType(argument)).writeValueAsString(bean)
    }

    @Override
    byte[] writeXmlAsBytes(Object bean) {
        databindXmlMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writeXmlAsBytes(Argument<?> argument, Object bean) {
        databindXmlMapper.writerFor(toJavaType(argument)).writeValueAsBytes(bean)
    }

    private JavaType toJavaType(Argument<?> argument) {
        if (!argument.typeParameters) {
            return databindXmlMapper.typeFactory.constructType(argument.type)
        }
        return databindXmlMapper.typeFactory.constructParametricType(
            argument.type,
            argument.typeParameters.collect { toJavaType(it) } as JavaType[]
        )
    }
}
