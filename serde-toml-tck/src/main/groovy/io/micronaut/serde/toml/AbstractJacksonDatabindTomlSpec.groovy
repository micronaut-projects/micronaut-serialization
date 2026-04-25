package io.micronaut.serde.toml

import io.micronaut.core.type.Argument
import tools.jackson.dataformat.toml.TomlMapper
import tools.jackson.databind.JavaType

import java.io.InputStream

abstract class AbstractJacksonDatabindTomlSpec extends AbstractTomlExtendedSerdeSpec {

    abstract TomlMapper getDatabindTomlMapper()

    @Override
    def <T> T readToml(String toml, Argument<T> type) {
        databindTomlMapper.readValue(toml, toJavaType(type))
    }

    @Override
    def <T> T readToml(byte[] toml, Argument<T> type) {
        databindTomlMapper.readValue(toml, toJavaType(type))
    }

    @Override
    def <T> T readToml(InputStream toml, Argument<T> type) {
        databindTomlMapper.readValue(toml, toJavaType(type))
    }

    @Override
    String writeToml(Object bean) {
        databindTomlMapper.writeValueAsString(bean)
    }

    @Override
    String writeToml(Argument<?> argument, Object bean) {
        databindTomlMapper.writerFor(toJavaType(argument)).writeValueAsString(bean)
    }

    @Override
    byte[] writeTomlAsBytes(Object bean) {
        databindTomlMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writeTomlAsBytes(Argument<?> argument, Object bean) {
        databindTomlMapper.writerFor(toJavaType(argument)).writeValueAsBytes(bean)
    }

    private JavaType toJavaType(Argument<?> argument) {
        if (!argument.typeParameters) {
            return databindTomlMapper.typeFactory.constructType(argument.type)
        }
        return databindTomlMapper.typeFactory.constructParametricType(
            argument.type,
            argument.typeParameters.collect { toJavaType(it) } as JavaType[]
        )
    }
}
