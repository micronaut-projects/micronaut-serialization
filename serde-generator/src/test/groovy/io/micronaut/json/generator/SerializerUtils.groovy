package io.micronaut.json.generator

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.Serializer
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JacksonDecoder
import io.micronaut.serde.jackson.JacksonEncoder
import io.micronaut.serde.reference.PropertyReference
import io.micronaut.serde.reference.SerializationReference
import org.intellij.lang.annotations.Language

trait SerializerUtils {
    @Language("json")
    static <T> String serializeToString(Serializer<T> serializer, T value, Class<?> view = Object.class) {
        def writer = new StringWriter()
        def generator = new JsonFactoryBuilder().build().createGenerator(writer)
        serializer.serialize(JacksonEncoder.create(generator), new MockEncoderContext(), value, Argument.OBJECT_ARGUMENT)
        generator.close()
        return writer.toString()
    }

    static <T> T deserializeFromString(Deserializer<T> serializer, @Language("json") String json, Class<?> view = Object.class) {
        def parser = new JsonFactoryBuilder().build().createParser(json)
        parser.nextToken() // place parser at first token
        return serializer.deserialize(JacksonDecoder.create(parser), new MockEncoderContext(), Argument.OBJECT_ARGUMENT)
    }

    private static class MockEncoderContext implements Serializer.EncoderContext, Deserializer.DecoderContext  {
        @Override
        def <B, P> SerializationReference<B, P> resolveReference(@NonNull SerializationReference<B, P> reference) {
            throw new UnsupportedOperationException()
        }

        @Override
        def <B, P> void pushManagedRef(@NonNull PropertyReference<B, P> reference) {
        }

        @Override
        void popManagedRef() {
        }

        @Override
        def <T> Serializer<? super T> findSerializer(@NonNull Argument<? extends T> forType) throws SerdeException {
            throw new UnsupportedOperationException()
        }

        def <T, D extends Serializer<? extends T>> D findCustomSerializer(@NonNull Class<? extends D> serializerClass) {
            throw new UnsupportedOperationException()
        }

        @Override
        def <B, P> PropertyReference<B, P> resolveReference(@NonNull PropertyReference<B, P> reference) {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> Deserializer<? extends T> findDeserializer(@NonNull Argument<? extends T> type) throws SerdeException {
            throw new UnsupportedOperationException()
        }

        @Override
        def <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
            throw new UnsupportedOperationException()
        }
        def
        <T, D extends Deserializer<? extends T>> D findCustomDeserializer(@NonNull Class<? extends D> deserializerClass)
                throws SerdeException {
            throw new UnsupportedOperationException()
        }
    }
}