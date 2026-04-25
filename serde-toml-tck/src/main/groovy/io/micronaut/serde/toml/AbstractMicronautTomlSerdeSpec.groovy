package io.micronaut.serde.toml

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper

import java.io.InputStream

abstract class AbstractMicronautTomlSerdeSpec extends AbstractTomlExtendedSerdeSpec {

    abstract ObjectMapper getTomlMapper()

    @Override
    def <T> T readToml(String toml, Argument<T> type) {
        tomlMapper.readValue(toml, type)
    }

    @Override
    def <T> T readToml(byte[] toml, Argument<T> type) {
        tomlMapper.readValue(toml, type)
    }

    @Override
    def <T> T readToml(InputStream toml, Argument<T> type) {
        tomlMapper.readValue(toml, type)
    }

    @Override
    String writeToml(Object bean) {
        tomlMapper.writeValueAsString(bean)
    }

    @Override
    String writeToml(Argument<?> argument, Object bean) {
        tomlMapper.writeValueAsString(argument, bean)
    }

    @Override
    byte[] writeTomlAsBytes(Object bean) {
        tomlMapper.writeValueAsBytes(bean)
    }

    @Override
    byte[] writeTomlAsBytes(Argument<?> argument, Object bean) {
        tomlMapper.writeValueAsBytes(argument, bean)
    }
}
