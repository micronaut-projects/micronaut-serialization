package io.micronaut.serde.toml

import io.micronaut.core.type.Argument
import tools.jackson.dataformat.toml.TomlMapper

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Map

trait TomlSpec {
    private static final TomlMapper TREE_MAPPER = new TomlMapper()

    abstract <T> T readToml(String toml, Argument<T> type)

    abstract <T> T readToml(byte[] toml, Argument<T> type)

    abstract <T> T readToml(InputStream toml, Argument<T> type)

    abstract String writeToml(Object bean)

    abstract String writeToml(Argument<?> argument, Object bean)

    abstract byte[] writeTomlAsBytes(Object bean)

    abstract byte[] writeTomlAsBytes(Argument<?> argument, Object bean)

    def <T> T readToml(String toml, Class<T> type) {
        readToml(toml, Argument.of(type))
    }

    def <T> T readToml(byte[] toml, Class<T> type) {
        readToml(toml, Argument.of(type))
    }

    def <T> T readToml(InputStream toml, Class<T> type) {
        readToml(toml, Argument.of(type))
    }

    byte[] tomlAsBytes(String toml) {
        toml.getBytes(StandardCharsets.UTF_8)
    }

    Map<String, Object> readTomlObject(String toml) {
        readToml(toml, Argument.mapOf(String, Object))
    }

    Map<String, Object> readTomlObject(byte[] toml) {
        readToml(toml, Argument.mapOf(String, Object))
    }

    Map<String, Object> readTomlObject(InputStream toml) {
        readToml(toml, Argument.mapOf(String, Object))
    }

    boolean tomlMatches(String result, String expected) {
        TREE_MAPPER.readTree(result) == TREE_MAPPER.readTree(expected)
    }

    boolean objRepresentationMatches(Object obj, String toml) {
        tomlMatches(writeToml(obj), toml)
    }

    boolean objRepresentationMatches(Argument<?> argument, Object obj, String toml) {
        tomlMatches(writeToml(argument, obj), toml)
    }

    def <T> T roundTrip(T obj) {
        roundTripAs(obj, Argument.of(obj.getClass()))
    }

    def <T> T roundTripAs(T obj, Argument<T> type) {
        readToml(writeToml(type, obj), type)
    }
}
