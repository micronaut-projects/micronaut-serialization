package io.micronaut.serde.xml.tck.jackson.databind

import io.micronaut.core.type.Argument
import io.micronaut.serde.xml.tck.XmlSpec
import tools.jackson.databind.JavaType
import tools.jackson.dataformat.xml.XmlMapper

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
