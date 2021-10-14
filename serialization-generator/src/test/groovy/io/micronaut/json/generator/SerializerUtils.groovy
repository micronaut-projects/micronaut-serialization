package io.micronaut.json.generator

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import io.micronaut.json.Deserializer
import io.micronaut.json.JacksonDecoder
import io.micronaut.json.JacksonEncoder
import io.micronaut.json.Serializer
import org.intellij.lang.annotations.Language

trait SerializerUtils {
    static final JsonFactory JSON_FACTORY = new JsonFactoryBuilder().build();

    @Language("json")
    static <T> String serializeToString(Serializer<T> serializer, T value, Class<?> view = Object.class) {
        def writer = new StringWriter()
        def generator = JSON_FACTORY.createGenerator(writer)
        serializer.serialize(JacksonEncoder.create(generator, view), value)
        generator.close()
        return writer.toString()
    }

    static <T> T deserializeFromString(Deserializer<T> serializer, @Language("json") String json, Class<?> view = Object.class) {
        def parser = JSON_FACTORY.createParser(json)
        parser.nextToken() // place parser at first token
        return serializer.deserialize(JacksonDecoder.create(parser, view))
    }
}